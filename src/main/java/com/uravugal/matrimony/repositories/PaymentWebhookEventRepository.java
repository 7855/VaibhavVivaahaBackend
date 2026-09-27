package com.uravugal.matrimony.repositories;

import com.uravugal.matrimony.models.PaymentWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, Long> {
    Optional<PaymentWebhookEvent> findByIdempotencyKey(String idempotencyKey);
    List<PaymentWebhookEvent> findByOrderIdOrderByIdDesc(String orderId);
}
