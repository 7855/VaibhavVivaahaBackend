package com.uravugal.matrimony.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * Government ID Verified Badge (simplified manual review).
 * User uploads a photo of a government ID (Aadhaar, PAN, Voter ID,
 * Driving License, Passport); admin reviews and approves. Silver+ only.
 *
 * Aadhaar XML offline eKYC with signature validation can be added later
 * as a second verification_type value without schema changes.
 */
@Entity
@Table(name = "id_verifications")
@Data
@EqualsAndHashCode(callSuper = true)
public class IdVerification extends GenericEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "userId", nullable = false)
    private Long userId;

    @Column(name = "document_url", nullable = false, length = 1000)
    private String documentUrl;

    /** AADHAAR_CARD, PAN_CARD, VOTER_ID, DRIVING_LICENSE, PASSPORT */
    @Column(name = "document_type", length = 32)
    private String documentType;

    /** Masked to last 4 digits only, for admin cross-reference. */
    @Column(name = "document_number_last4", length = 16)
    private String documentNumberLast4;

    /** PENDING, VERIFIED, REJECTED */
    @Column(name = "status", length = 32, nullable = false)
    private String status = "PENDING";

    @Column(name = "reviewed_by_admin_id")
    private Long reviewedByAdminId;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;
}