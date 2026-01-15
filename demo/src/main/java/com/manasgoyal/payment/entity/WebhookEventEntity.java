package com.manasgoyal.payment.entity;

import com.manasgoyal.payment.entity.enums.PaymentProvider;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "webhook_events",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_webhook_provider_event", columnNames = {"provider", "eventId"})
        }
)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentProvider provider;

    // Stripe provides event id (evt_...)
    // Razorpay also has event id in payload.
    @Column(nullable = false)
    private String eventId;

    @Lob
    @Column(nullable = false)
    private String payload;

    @Column(nullable = false, updatable = false)
    private Instant receivedAt;

    @PrePersist
    public void prePersist() {
        this.receivedAt = Instant.now();
    }
}
