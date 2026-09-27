package com.uravugal.matrimony.models;

import com.uravugal.matrimony.enums.ActiveStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

/**
 * Every webhook delivery Cashfree makes to us, exact bytes included — signature
 * failures too, for forensics. {@code idempotencyKey} (Cashfree's x-idempotency-key,
 * unique per payload) is what lets a redelivery be recognised and answered 200
 * without re-running side effects. Cashfree delivers at-least-once and retries at
 * 2 / 10 / 30 minutes until it gets a 200, so duplicates are the normal case, not
 * an edge case.
 */
@Data
@Entity
@Table(name = "payment_webhook_events",
        uniqueConstraints = @UniqueConstraint(name = "uk_pwe_idem", columnNames = "idempotencyKey"),
        indexes = @Index(name = "idx_pwe_order", columnList = "orderId"))
public class PaymentWebhookEvent extends GenericEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotencyKey", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "eventType", length = 64)
    private String eventType;

    @Column(name = "orderId", length = 45)
    private String orderId;

    @Column(name = "cfPaymentId", length = 64)
    private String cfPaymentId;

    @Column(name = "webhookVersion", length = 16)
    private String webhookVersion;

    @Column(name = "attempt", length = 8)
    private String attempt;

    @Column(name = "signatureValid", length = 8, columnDefinition = "enum('Y','N') default 'N'")
    @Enumerated(EnumType.STRING)
    private ActiveStatus signatureValid = ActiveStatus.N;

    @Column(name = "processed", length = 8, columnDefinition = "enum('Y','N') default 'N'")
    @Enumerated(EnumType.STRING)
    private ActiveStatus processed = ActiveStatus.N;

    @Column(name = "processingError", length = 1000)
    private String processingError;

    @Column(name = "rawPayload", columnDefinition = "LONGTEXT")
    private String rawPayload;
}
