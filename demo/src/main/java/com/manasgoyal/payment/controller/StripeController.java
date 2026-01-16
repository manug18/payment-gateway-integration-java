package com.manasgoyal.payment.controller;

import com.manasgoyal.payment.dto.CreateStripeCheckoutRequest;
import com.manasgoyal.payment.dto.CreateStripeCheckoutResponse;
import com.manasgoyal.payment.service.StripePaymentService;
import com.stripe.exception.StripeException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments/stripe")
@RequiredArgsConstructor
public class StripeController {

    private final StripePaymentService stripePaymentService;

    @PostMapping("/checkout")
    public CreateStripeCheckoutResponse createCheckout(@RequestBody @Valid CreateStripeCheckoutRequest req)
            throws StripeException {
        return stripePaymentService.createCheckoutSession(req.orderId());
    }
}
