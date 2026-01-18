package com.manasgoyal.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manasgoyal.payment.dto.CreateRazorpayOrderResponse;
import com.manasgoyal.payment.dto.RazorpayVerifyPaymentRequest;
import com.manasgoyal.payment.entity.OrderEntity;
import com.manasgoyal.payment.entity.PaymentEntity;
import com.manasgoyal.payment.entity.WebhookEventEntity;
import com.manasgoyal.payment.entity.enums.OrderStatus;
import com.manasgoyal.payment.entity.enums.PaymentProvider;
import com.manasgoyal.payment.entity.enums.PaymentStatus;
import com.manasgoyal.payment.repository.OrderRepository;
import com.manasgoyal.payment.repository.PaymentRepository;
import com.manasgoyal.payment.repository.WebhookEventRepository;
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

@Service
@RequiredArgsConstructor
public class RazorpayPaymentService {

    private final RazorpayClient razorpayClient;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final WebhookEventRepository webhookEventRepository;

    @Value("${razorpay.keyId}")
    private String keyId;

    @Value("${razorpay.keySecret}")
    private String keySecret;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public CreateRazorpayOrderResponse createRazorpayOrder(Long orderId) throws Exception {

        OrderEntity orderEntity = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        long amountInPaise = orderEntity.getAmount()
                .multiply(java.math.BigDecimal.valueOf(100))
                .longValue();

        JSONObject request = new JSONObject();
        request.put("amount", amountInPaise);
        request.put("currency", orderEntity.getCurrency());
        request.put("receipt", "order_rcpt_" + orderId);
        request.put("payment_capture", 1);

        Order rzOrder = razorpayClient.orders.create(request);

        // Create payment row in DB
        PaymentEntity payment = paymentRepository.findByOrderId(orderId)
                .orElseGet(() -> paymentRepository.save(
                        PaymentEntity.builder()
                                .orderId(orderId)
                                .provider(PaymentProvider.RAZORPAY)
                                .status(PaymentStatus.PENDING)
                                .build()
                ));

        payment.setProviderOrderId(rzOrder.get("id")); // order_...
        paymentRepository.save(payment);

        return new CreateRazorpayOrderResponse(
                keyId,
                rzOrder.get("id"),
                amountInPaise,
                orderEntity.getCurrency()
        );
    }

    /**
     * Verify signature from frontend (Razorpay checkout response).
     */
    @Transactional
    public void verifyPayment(RazorpayVerifyPaymentRequest req) {

        // signature string format:
        // order_id|payment_id
        String data = req.razorpayOrderId() + "|" + req.razorpayPaymentId();
        String generatedSignature = hmacSha256Hex(data, keySecret);

        if (!generatedSignature.equals(req.razorpaySignature())) {
            throw new RuntimeException("Invalid Razorpay signature");
        }

        // Find payment by razorpay order_id
        PaymentEntity payment = paymentRepository.findByOrderId(req.orderId())
                .orElseThrow(() -> new RuntimeException("Payment not found for orderId: " + req.orderId()));

        payment.setProviderOrderId(req.razorpayOrderId());
        payment.setProviderPaymentId(req.razorpayPaymentId());
        payment.setStatus(PaymentStatus.PAID);
        paymentRepository.save(payment);

        OrderEntity order = orderRepository.findById(req.orderId())
                .orElseThrow(() -> new RuntimeException("Order not found: " + req.orderId()));

        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);
    }

    /**
     * Handle webhook (optional but portfolio-strong).
     */
    @Transactional
    public void handleWebhook(String rawPayload, String webhookEventId) {
        // idempotency check (use eventId)
        if (webhookEventRepository.existsByProviderAndEventId(PaymentProvider.RAZORPAY, webhookEventId)) {
            return;
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
            JsonNode payload = root.path("payload");

            if ("payment.captured".equals(event)) {
                String razorpayPaymentId = payload.path("payment").path("entity").path("id").asText();
                String razorpayOrderId = payload.path("payment").path("entity").path("order_id").asText();

                PaymentEntity payment = paymentRepository.findAll().stream()
                        .filter(p -> razorpayOrderId.equals(p.getProviderOrderId()))
                        .findFirst()
                        .orElse(null);

                if (payment != null) {
                    payment.setProviderPaymentId(razorpayPaymentId);
                    payment.setStatus(PaymentStatus.PAID);
                    paymentRepository.save(payment);

                    OrderEntity order = orderRepository.findById(payment.getOrderId()).orElse(null);
                    if (order != null) {
                        order.setStatus(OrderStatus.PAID);
                        orderRepository.save(order);
                    }
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to process Razorpay webhook payload", e);
        }
    }

    // --- helpers ---

    private String hmacSha256Hex(String data, String secret) {
        try {
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256Hmac.init(secretKey);
            byte[] hash = sha256Hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC SHA256", e);
        }
    }
}
