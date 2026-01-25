package com.manasgoyal.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manasgoyal.payment.client.OrderClient;
import com.manasgoyal.payment.dto.CreateStripeCheckoutResponse;
import com.manasgoyal.payment.dto.PaymentStatusUpdateRequest;
import com.manasgoyal.payment.entity.PaymentEntity;
import com.manasgoyal.payment.entity.WebhookEventEntity;
import com.manasgoyal.payment.entity.enums.OrderStatus;
import com.manasgoyal.payment.entity.enums.PaymentProvider;
import com.manasgoyal.payment.entity.enums.PaymentStatus;
import com.manasgoyal.payment.repository.PaymentRepository;
import com.manasgoyal.payment.repository.WebhookEventRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StripePaymentService {

    private final PaymentRepository paymentRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final OrderClient orderClient; // ⭐ talk to order-service
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CreateStripeCheckoutResponse createCheckoutSession(UUID orderId) throws StripeException {

        // 1️⃣ Fetch order from order-service
        var order = orderClient.getOrder(orderId);

        long amount = order.totalAmount();     // already in smallest unit
        String currency = order.currency();

        // 2️⃣ Create or reuse payment record
        PaymentEntity payment = paymentRepository.findByOrderId(orderId)
                .orElseGet(() -> paymentRepository.save(
                        PaymentEntity.builder()
                                .orderId(orderId)
                                .provider(PaymentProvider.STRIPE)
                                .status(PaymentStatus.CREATED)
                                .build()
                ));

        // 3️⃣ Create Stripe checkout session
        SessionCreateParams params =
                SessionCreateParams.builder()
                        .setMode(SessionCreateParams.Mode.PAYMENT)
                        .setSuccessUrl("http://localhost:8080/success")
                        .setCancelUrl("http://localhost:8080/cancel")
                        .addLineItem(
                                SessionCreateParams.LineItem.builder()
                                        .setQuantity(1L)
                                        .setPriceData(
                                                SessionCreateParams.LineItem.PriceData.builder()
                                                        .setCurrency(currency.toLowerCase())
                                                        .setUnitAmount(amount)
                                                        .setProductData(
                                                                SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                        .setName("Order #" + orderId)
                                                                        .build()
                                                        )
                                                        .build()
                                        )
                                        .build()
                        )
                        .putMetadata("orderId", orderId.toString())
                        .build();

        Session session = Session.create(params);

        // 4️⃣ Save session info
        payment.setStatus(PaymentStatus.PENDING);
        payment.setProviderSessionId(session.getId());
        paymentRepository.save(payment);

        // 5️⃣ Send checkout URL to frontend
        return new CreateStripeCheckoutResponse(session.getUrl());
    }


    @Transactional
    public void handleStripeEvent(Event event, String rawPayload) {

        if (webhookEventRepository.existsByProviderAndEventId(PaymentProvider.STRIPE, event.getId())) {
            return;
        }

        webhookEventRepository.save(
                WebhookEventEntity.builder()
                        .provider(PaymentProvider.STRIPE)
                        .eventId(event.getId())
                        .payload(rawPayload)
                        .build()
        );

        switch (event.getType()) {
            case "checkout.session.completed" -> handleCheckoutSessionCompleted(rawPayload);
            case "payment_intent.payment_failed" -> handlePaymentIntentFailed(rawPayload);
        }
    }

    private void handleCheckoutSessionCompleted(String rawPayload) {
        try {
            JsonNode obj = objectMapper.readTree(rawPayload)
                    .path("data").path("object");

            String sessionId = obj.path("id").asText();
            String paymentIntentId = obj.path("payment_intent").asText();

            PaymentEntity payment = paymentRepository.findByProviderSessionId(sessionId).orElse(null);
            if (payment == null) return;

            payment.setStatus(PaymentStatus.PAID);
            payment.setProviderPaymentId(paymentIntentId);
            paymentRepository.save(payment);

            // 🔥 notify order-service
            orderClient.updatePaymentStatus(
                    payment.getOrderId(),
                    new PaymentStatusUpdateRequest(PaymentStatus.PAID, paymentIntentId)
            );

        } catch (Exception e) {
            throw new RuntimeException("Stripe success webhook error", e);
        }
    }

    private void handlePaymentIntentFailed(String rawPayload) {
        try {
            JsonNode obj = objectMapper.readTree(rawPayload)
                    .path("data").path("object");

            String paymentIntentId = obj.path("id").asText();

            PaymentEntity payment = paymentRepository.findByProviderPaymentId(paymentIntentId).orElse(null);
            if (payment == null) return;

            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);

            // 🔥 notify order-service
            orderClient.updatePaymentStatus(
                    payment.getOrderId(),
                    new PaymentStatusUpdateRequest(PaymentStatus.FAILED, paymentIntentId)
            );

        } catch (Exception e) {
            throw new RuntimeException("Stripe failure webhook error", e);
        }
    }
}
