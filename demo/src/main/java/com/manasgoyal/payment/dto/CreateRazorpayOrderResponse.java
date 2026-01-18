package com.manasgoyal.payment.dto;

public record CreateRazorpayOrderResponse(
        String razorpayKeyId,
        String razorpayOrderId,
        Long amount,     // in paise
        String currency
) {}
