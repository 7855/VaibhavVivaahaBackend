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
 * F8 — Salary / Income Verified Badge.
 * User uploads a salary slip / ITR / offer letter PDF; admin manually
 * reviews and approves. On approval the user's income_verified flag
 * flips to true and the badge appears on their profile.
 *
 * Gold+ only (gating handled in IncomeVerificationService).
 */
@Entity
@Table(name = "income_verifications")
@Data
@EqualsAndHashCode(callSuper = true)
public class IncomeVerification extends GenericEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "userId", nullable = false)
    private Long userId;

    /** S3 URL of the uploaded document. */
    @Column(name = "document_url", nullable = false, length = 1000)
    private String documentUrl;

    /** SALARY_SLIP, ITR, OFFER_LETTER, OTHER */
    @Column(name = "document_type", length = 32)
    private String documentType;

    /** Free-form, e.g. "12 LPA" or "₹85,000/month". Display only. */
    @Column(name = "claimed_annual_income", length = 64)
    private String claimedAnnualIncome;

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