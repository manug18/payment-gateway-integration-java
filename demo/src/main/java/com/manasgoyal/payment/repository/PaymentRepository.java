package com.manasgoyal.payment.repository;

import com.manasgoyal.payment.entity.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {
    Optional<PaymentEntity> findByProviderSessionId(String providerSessionId);
    Optional<PaymentEntity> findByOrderId(Long orderId);
    Optional<PaymentEntity> findByProviderPaymentId(String providerPaymentId);

}
