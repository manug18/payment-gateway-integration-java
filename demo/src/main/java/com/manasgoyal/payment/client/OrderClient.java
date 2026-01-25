package com.manasgoyal.payment.client;

import com.manasgoyal.payment.dto.OrderSummary;
import com.manasgoyal.payment.dto.PaymentStatusUpdateRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;
@FeignClient(name = "order-service", url = "${order.service.url}")
public interface OrderClient {

    @GetMapping("/api/orders/{id}")
    OrderSummary getOrder(@PathVariable UUID id);

    @PutMapping("/api/orders/{id}/payment-status")
    void updatePaymentStatus(
            @PathVariable UUID id,
            @RequestBody PaymentStatusUpdateRequest request
    );
}

