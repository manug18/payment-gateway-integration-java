package com.manasgoyal.payment.repository;

import com.manasgoyal.payment.entity.PaymentEntity;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {
    Optional<PaymentEntity> findByProviderSessionId(String providerSessionId);
    Optional<PaymentEntity> findByOrderId(@NotNull UUID orderId);
    Optional<PaymentEntity> findByProviderPaymentId(String providerPaymentId);

}
