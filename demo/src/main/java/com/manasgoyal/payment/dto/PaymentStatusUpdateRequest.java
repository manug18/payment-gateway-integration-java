package com.manasgoyal.payment.dto;

import com.manasgoyal.payment.entity.enums.PaymentStatus;

public record PaymentStatusUpdateRequest(
        PaymentStatus paymentStatus,
        String paymentReferenceId
) {}
