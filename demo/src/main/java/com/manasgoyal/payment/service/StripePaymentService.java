package com.manasgoyal.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manasgoyal.payment.dto.CreateStripeCheckoutResponse;
import com.manasgoyal.payment.entity.OrderEntity;
import com.manasgoyal.payment.entity.PaymentEntity;
import com.manasgoyal.payment.entity.WebhookEventEntity;
import com.manasgoyal.payment.entity.enums.OrderStatus;
import com.manasgoyal.payment.entity.enums.PaymentProvider;
import com.manasgoyal.payment.entity.enums.PaymentStatus;
import com.manasgoyal.payment.repository.OrderRepository;
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

@Service
@RequiredArgsConstructor
public class StripePaymentService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final WebhookEventRepository webhookEventRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();


    public CreateStripeCheckoutResponse createCheckoutSession(Long orderId) throws StripeException {

        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        // Create or reuse payment record
        PaymentEntity payment = paymentRepository.findByOrderId(orderId)
                .orElseGet(() -> paymentRepository.save(
                        PaymentEntity.builder()
                                .orderId(orderId)
                                .provider(PaymentProvider.STRIPE)
                                .status(PaymentStatus.CREATED)
                                .build()
                ));

        // Amount in smallest currency unit:
        // INR: paise, USD: cents
        long amount = order.getAmount().multiply(java.math.BigDecimal.valueOf(100)).longValue();

        SessionCreateParams params =
                SessionCreateParams.builder()
                        .setMode(SessionCreateParams.Mode.PAYMENT)
                        .setSuccessUrl("http://localhost:8080/success") // can be dummy for now
                        .setCancelUrl("http://localhost:8080/cancel")
                        .addLineItem(
                                SessionCreateParams.LineItem.builder()
                                        .setQuantity(1L)
                                        .setPriceData(
                                                SessionCreateParams.LineItem.PriceData.builder()
                                                        .setCurrency(order.getCurrency().toLowerCase())
                                                        .setUnitAmount(amount)
                                                        .setProductData(
                                                                SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                        .setName("Order #" + order.getId())
                                                                        .build()
                                                        )
                                                        .build()
                                        )
                                        .build()
                        )
                        .putMetadata("orderId", String.valueOf(orderId))
                        .build();

        Session session = Session.create(params);

        // update payment with session
        payment.setStatus(PaymentStatus.PENDING);
        payment.setProviderSessionId(session.getId());
        paymentRepository.save(payment);

        return new CreateStripeCheckoutResponse(session.getUrl());
    }

    /**
     * Handle webhook event idempotently.
     */
    @Transactional
    public void handleStripeEvent(Event event, String rawPayload) {

        // 1) idempotency
        if (webhookEventRepository.existsByProviderAndEventId(PaymentProvider.STRIPE, event.getId())) {
            return;
        }

        // 2) store webhook payload
        webhookEventRepository.save(
                WebhookEventEntity.builder()
                        .provider(PaymentProvider.STRIPE)
                        .eventId(event.getId())
                        .payload(rawPayload)
                        .build()
        );

        // 3) handle event types
        switch (event.getType()) {
            case "checkout.session.completed" -> handleCheckoutSessionCompleted(rawPayload);
            case "payment_intent.payment_failed" -> handlePaymentIntentFailed(rawPayload);
            default -> {
                // ignore other events
            }
        }
    }
    private void handleCheckoutSessionCompleted(String rawPayload) {
        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            JsonNode obj = root.path("data").path("object");

            // checkout session id (cs_test_...)
            String sessionId = obj.path("id").asText();

            // payment intent id (pi_...)
            String paymentIntentId = obj.path("payment_intent").asText(null);

            if (sessionId == null || sessionId.isBlank()) {
                System.out.println("❌ sessionId missing in webhook payload");
                return;
            }

            PaymentEntity payment = paymentRepository.findByProviderSessionId(sessionId).orElse(null);

            if (payment == null) {
                System.out.println("❌ No payment found for sessionId=" + sessionId);
                return;
            }

            // if already paid => ignore (retry-safe)
            if (payment.getStatus() == PaymentStatus.PAID) {
                return;
            }

            payment.setStatus(PaymentStatus.PAID);

            // store pi_... for later reconciliation/refunds
            if (paymentIntentId != null && !paymentIntentId.isBlank()) {
                payment.setProviderPaymentId(paymentIntentId);
            }

            paymentRepository.save(payment);

            OrderEntity order = orderRepository.findById(payment.getOrderId()).orElse(null);
            if (order != null) {
                order.setStatus(OrderStatus.PAID);
                orderRepository.save(order);
            }

            System.out.println("✅ Marked PAID: sessionId=" + sessionId + ", paymentIntentId=" + paymentIntentId);

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse checkout.session.completed payload", e);
        }
    }


    private void handlePaymentIntentFailed(String rawPayload) {
        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            JsonNode obj = root.path("data").path("object");

            // payment_intent id (pi_...)
            String paymentIntentId = obj.path("id").asText();

            if (paymentIntentId == null || paymentIntentId.isBlank()) {
                System.out.println("❌ paymentIntentId missing in webhook payload");
                return;
            }

            // find payment by providerPaymentId (pi_...)
            PaymentEntity payment = paymentRepository.findByProviderPaymentId(paymentIntentId).orElse(null);

            if (payment == null) {
                System.out.println("❌ No payment found for paymentIntentId=" + paymentIntentId);
                return;
            }

            // if already paid => do not overwrite
            if (payment.getStatus() == PaymentStatus.PAID) {
                return;
            }

            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);

            OrderEntity order = orderRepository.findById(payment.getOrderId()).orElse(null);
            if (order != null) {
                order.setStatus(OrderStatus.PAYMENT_FAILED);
                orderRepository.save(order);
            }

            System.out.println("❌ Marked FAILED: paymentIntentId=" + paymentIntentId);

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse payment_intent.payment_failed payload", e);
        }
    }




    private void handleCheckoutSessionCompleted(Event event) {
        Optional<Session> sessionOptional = event.getDataObjectDeserializer()
                .getObject()
                .map(obj -> (Session) obj);

        if (sessionOptional.isEmpty()) return;

        Session session = sessionOptional.get();
        String sessionId = session.getId();

        PaymentEntity payment = paymentRepository.findByProviderSessionId(sessionId)
                .orElse(null);

        if (payment == null) return;

        // Update payment
        payment.setStatus(PaymentStatus.PAID);
        paymentRepository.save(payment);

        // Update order
        OrderEntity order = orderRepository.findById(payment.getOrderId())
                .orElse(null);

        if (order != null) {
            order.setStatus(OrderStatus.PAID);
            orderRepository.save(order);
        }
    }
    private void markPaymentSuccessFromPayload(String rawPayload) {
        try {
            JsonNode root = objectMapper.readTree(rawPayload);

            // Stripe webhook payload structure:
            // data.object.id = "cs_test_..."
            String sessionId = root.path("data").path("object").path("id").asText();

            if (sessionId == null || sessionId.isBlank()) {
                System.out.println("❌ sessionId missing in webhook payload");
                return;
            }

            PaymentEntity payment = paymentRepository.findByProviderSessionId(sessionId).orElse(null);

            if (payment == null) {
                System.out.println("❌ No payment found for sessionId=" + sessionId);
                return;
            }

            // update payment + order
            payment.setStatus(PaymentStatus.PAID);
            paymentRepository.save(payment);

            OrderEntity order = orderRepository.findById(payment.getOrderId()).orElse(null);
            if (order != null) {
                order.setStatus(OrderStatus.PAID);
                orderRepository.save(order);
            }

            System.out.println("✅ Payment marked PAID for sessionId=" + sessionId);

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Stripe webhook payload", e);
        }
    }
}
