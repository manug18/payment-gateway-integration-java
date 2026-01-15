package com.manasgoyal.payment.dto;

import com.manasgoyal.payment.entity.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record OrderResponse(
        Long id,
        BigDecimal amount,
        String currency,
        OrderStatus status,
        Instant createdAt
) {}
