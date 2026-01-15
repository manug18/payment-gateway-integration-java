package com.manasgoyal.payment.service;

import com.manasgoyal.payment.dto.CreateOrderRequest;
import com.manasgoyal.payment.dto.OrderResponse;
import com.manasgoyal.payment.entity.OrderEntity;
import com.manasgoyal.payment.entity.enums.OrderStatus;
import com.manasgoyal.payment.repository.OrderRepository;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public OrderResponse createOrder(CreateOrderRequest req) {
        OrderEntity order = OrderEntity.builder()
                .amount(req.amount())
                .currency(req.currency().toUpperCase())
                .status(OrderStatus.CREATED)
                .build();

        order = orderRepository.save(order);

        return new OrderResponse(
                order.getId(),
                order.getAmount(),
                order.getCurrency(),
                order.getStatus(),
                order.getCreatedAt()
        );
    }

    public OrderResponse getOrder(Long id) {
        OrderEntity order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found: " + id));

        return new OrderResponse(
                order.getId(),
                order.getAmount(),
                order.getCurrency(),
                order.getStatus(),
                order.getCreatedAt()
        );
    }
}
