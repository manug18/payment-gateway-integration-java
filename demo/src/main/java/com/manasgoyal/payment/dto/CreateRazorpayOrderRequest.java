package com.manasgoyal.payment.dto;

import jakarta.validation.constraints.NotNull;

public record CreateRazorpayOrderRequest(
        @NotNull Long orderId
) {}
