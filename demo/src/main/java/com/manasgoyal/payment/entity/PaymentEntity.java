package com.manasgoyal.payment.entity;

import com.manasgoyal.payment.entity.enums.PaymentProvider;
import com.manasgoyal.payment.entity.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "payments")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    // Stripe: payment_intent id OR Razorpay: payment_id
    private String providerPaymentId;

    // Stripe checkout session id (cs_test_...)
    private String providerSessionId;

    // Razorpay: order_id
    private String providerOrderId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = Instant.now();
    }
}
