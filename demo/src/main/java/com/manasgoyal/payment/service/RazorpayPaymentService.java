package com.manasgoyal.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manasgoyal.payment.client.OrderClient;
import com.manasgoyal.payment.dto.CreateRazorpayOrderResponse;
import com.manasgoyal.payment.dto.RazorpayVerifyPaymentRequest;
import com.manasgoyal.payment.entity.PaymentEntity;
import com.manasgoyal.payment.entity.WebhookEventEntity;
import com.manasgoyal.payment.entity.enums.PaymentProvider;
import com.manasgoyal.payment.entity.enums.PaymentStatus;
import com.manasgoyal.payment.repository.PaymentRepository;
import com.manasgoyal.payment.repository.WebhookEventRepository;
import com.manasgoyal.payment.dto.PaymentStatusUpdateRequest;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RazorpayPaymentService {

    private final RazorpayClient razorpayClient;
    private final PaymentRepository paymentRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final OrderClient orderClient; // ⭐ talk to order-service

    @Value("${razorpay.keyId}")
    private String keyId;

    @Value("${razorpay.keySecret}")
    private String keySecret;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Called by order-service to create payment at Razorpay
     */
    public CreateRazorpayOrderResponse createRazorpayOrder(UUID orderId) throws Exception {

        // 1️⃣ Fetch order from Order Service
        var order = orderClient.getOrder(orderId);

        long amountInPaise = order.totalAmount(); // already in paise from order-service
        String currency = order.currency();

        // 2️⃣ Create Razorpay order
        JSONObject request = new JSONObject();
        request.put("amount", amountInPaise);
        request.put("currency", currency);
        request.put("receipt", "ord_" + orderId.toString().replace("-", "").substring(0, 30));
        request.put("payment_capture", 1);

        Order rzOrder = razorpayClient.orders.create(request);

        // 3️⃣ Save payment record
        PaymentEntity payment = paymentRepository.save(
                PaymentEntity.builder()
                        .orderId(orderId)
                        .provider(PaymentProvider.RAZORPAY)
                        .providerOrderId(rzOrder.get("id"))
                        .status(PaymentStatus.PENDING)
                        .build()
        );

        // 4️⃣ Send data to frontend
        return new CreateRazorpayOrderResponse(
                keyId,
                rzOrder.get("id"),
                amountInPaise,
                currency
        );
    }


    /**
     * Frontend signature verification
     */
    @Transactional
    public void verifyPayment(RazorpayVerifyPaymentRequest req) {

        String data = req.razorpayOrderId() + "|" + req.razorpayPaymentId();
        String generatedSignature = hmacSha256Hex(data, keySecret);

        if (!generatedSignature.equals(req.razorpaySignature())) {
            throw new RuntimeException("Invalid Razorpay signature");
        }

        PaymentEntity payment = paymentRepository.findByOrderId(req.orderId())
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        payment.setProviderPaymentId(req.razorpayPaymentId());
        payment.setStatus(PaymentStatus.PAID);
        paymentRepository.save(payment);

        // 🔥 Notify order-service
        orderClient.updatePaymentStatus(
                req.orderId(),
                new PaymentStatusUpdateRequest(PaymentStatus.PAID, req.razorpayPaymentId())
        );
    }

    /**
     * Webhook handler (gateway → payment-service)
     */
    @Transactional
    public void handleWebhook(String rawPayload, String webhookEventId) {

        if (webhookEventRepository.existsByProviderAndEventId(PaymentProvider.RAZORPAY, webhookEventId)) {
            return; // idempotent
        }

        webhookEventRepository.save(
                WebhookEventEntity.builder()
                        .provider(PaymentProvider.RAZORPAY)
                        .eventId(webhookEventId)
                        .payload(rawPayload)
                        .build()
        );

        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            String event = root.path("event").asText();

            if ("payment.captured".equals(event)) {
                JsonNode entity = root.path("payload").path("payment").path("entity");

                String razorpayPaymentId = entity.path("id").asText();
                String razorpayOrderId = entity.path("order_id").asText();

                PaymentEntity payment = paymentRepository.findAll().stream()
                        .filter(p -> razorpayOrderId.equals(p.getProviderOrderId()))
                        .findFirst()
                        .orElse(null);

                if (payment != null) {
                    payment.setProviderPaymentId(razorpayPaymentId);
                    payment.setStatus(PaymentStatus.PAID);
                    paymentRepository.save(payment);

                    // 🔥 Notify order-service
                    orderClient.updatePaymentStatus(
                            payment.getOrderId(),
                            new PaymentStatusUpdateRequest(PaymentStatus.PAID, razorpayPaymentId)
                    );
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Webhook processing failed", e);
        }
    }

    private String hmacSha256Hex(String data, String secret) {
        try {
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256Hmac.init(secretKey);
            byte[] hash = sha256Hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("HMAC error", e);
        }
    }
}
