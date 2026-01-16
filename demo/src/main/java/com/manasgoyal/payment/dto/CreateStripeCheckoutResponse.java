package com.manasgoyal.payment.dto;

public record CreateStripeCheckoutResponse(
        String checkoutUrl
) {}
