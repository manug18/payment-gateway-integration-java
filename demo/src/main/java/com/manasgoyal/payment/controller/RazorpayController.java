package com.manasgoyal.payment.controller;

import com.manasgoyal.payment.dto.CreateRazorpayOrderRequest;
import com.manasgoyal.payment.dto.CreateRazorpayOrderResponse;
import com.manasgoyal.payment.dto.RazorpayVerifyPaymentRequest;
import com.manasgoyal.payment.service.RazorpayPaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments/razorpay")
@RequiredArgsConstructor
public class RazorpayController {

    private final RazorpayPaymentService razorpayPaymentService;

    @PostMapping("/order")
    public CreateRazorpayOrderResponse createOrder(@RequestBody @Valid CreateRazorpayOrderRequest req)
            throws Exception {
        return razorpayPaymentService.createRazorpayOrder(req.orderId());
    }

    @PostMapping("/verify")
    public String verify(@RequestBody @Valid RazorpayVerifyPaymentRequest req) {
        razorpayPaymentService.verifyPayment(req);
        return "✅ Verified & marked as PAID";
    }
}
