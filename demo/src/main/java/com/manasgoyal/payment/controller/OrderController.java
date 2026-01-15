package com.manasgoyal.payment.controller;

import com.manasgoyal.payment.dto.CreateOrderRequest;
import com.manasgoyal.payment.dto.OrderResponse;
import com.manasgoyal.payment.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public OrderResponse create(@RequestBody @Valid CreateOrderRequest req) {
        return orderService.createOrder(req);
    }

    @GetMapping("/{id}")
    public OrderResponse getById(@PathVariable Long id) {
        return orderService.getOrder(id);
    }
}
