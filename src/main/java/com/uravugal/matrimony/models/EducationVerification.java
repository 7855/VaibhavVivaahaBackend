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
 * Education Verified Badge.
 * User uploads a degree certificate / diploma / mark sheet;
 * admin manually reviews and approves. Silver+ only.
 */
@Entity
@Table(name = "education_verifications")
@Data
@EqualsAndHashCode(callSuper = true)
public class EducationVerification extends GenericEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "userId", nullable = false)
    private Long userId;

    @Column(name = "document_url", nullable = false, length = 1000)
    private String documentUrl;

    /** DEGREE_CERTIFICATE, DIPLOMA, PROFESSIONAL_CERT, MARK_SHEET, OTHER */
    @Column(name = "document_type", length = 32)
    private String documentType;

    @Column(name = "institution_name", length = 200)
    private String institutionName;

    @Column(name = "qualification", length = 200)
    private String qualification;

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