package com.manasgoyal.payment.controller;

import com.manasgoyal.payment.service.RazorpayPaymentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class RazorpayWebhookController {

    private final RazorpayPaymentService razorpayPaymentService;

    @Value("${razorpay.webhookSecret}")
    private String webhookSecret;

    @PostMapping("/razorpay")
    public ResponseEntity<String> handle(HttpServletRequest request) throws IOException {

        String payload = StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8);

        String signature = request.getHeader("X-Razorpay-Signature");
        String eventId = request.getHeader("X-Razorpay-Event-Id");

        if (signature == null || eventId == null) {
            return ResponseEntity.badRequest().body("Missing Razorpay headers");
        }

        // Verify webhook signature
        String expected = hmacSha256Hex(payload, webhookSecret);

        if (!expected.equals(signature)) {
            return ResponseEntity.status(400).body("Invalid signature");
        }

        razorpayPaymentService.handleWebhook(payload, eventId);

        return ResponseEntity.ok("ok");
    }

    private String hmacSha256Hex(String data, String secret) {
        try {
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256Hmac.init(secretKey);
            byte[] hash = sha256Hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC", e);
        }
    }
}
