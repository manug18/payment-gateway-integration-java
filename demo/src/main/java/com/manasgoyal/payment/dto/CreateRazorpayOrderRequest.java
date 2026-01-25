package com.manasgoyal.payment.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateRazorpayOrderRequest(
        @NotNull UUID orderId
) {}
