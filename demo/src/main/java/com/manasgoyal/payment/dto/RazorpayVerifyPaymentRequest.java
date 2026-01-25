package com.manasgoyal.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RazorpayVerifyPaymentRequest(
        @NotNull UUID orderId,
        @NotBlank String razorpayOrderId,
        @NotBlank String razorpayPaymentId,
        @NotBlank String razorpaySignature
) {}
