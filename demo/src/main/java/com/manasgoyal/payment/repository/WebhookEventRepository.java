package com.manasgoyal.payment.repository;

import com.manasgoyal.payment.entity.WebhookEventEntity;
import com.manasgoyal.payment.entity.enums.PaymentProvider;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEventRepository extends JpaRepository<WebhookEventEntity, Long> {
    boolean existsByProviderAndEventId(PaymentProvider provider, String eventId);
}
