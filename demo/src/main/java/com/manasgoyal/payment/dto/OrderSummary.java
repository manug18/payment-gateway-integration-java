package com.manasgoyal.payment.dto;


import java.util.UUID;

public record OrderSummary(
        UUID id,
        long totalAmount,
        String currency
) {}

