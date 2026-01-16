package com.manasgoyal.payment.dto;

import jakarta.validation.constraints.NotNull;

public record CreateStripeCheckoutRequest(
        @NotNull Long orderId
) {}
