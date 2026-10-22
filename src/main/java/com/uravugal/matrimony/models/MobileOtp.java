package com.uravugal.matrimony.models;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * One row per mobile number going through WhatsApp OTP verification — the
 * mobile twin of {@link UserLead} (which is keyed on email). Registration and
 * "add-mobile" from the profile page both use it: the code is sent on
 * WhatsApp through Zoho CPaaS, verifying it mints a short-lived
 * verificationToken that createUser / addMobile then spend.
 */
@Data
@Entity
@Table(name = "mobile_otps", indexes = @Index(name = "mobile_otps_mobile_uq", columnList = "mobile", unique = true))
public class MobileOtp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 15)
    private String mobile;

    @Column(length = 8)
    private String otp;

    private LocalDateTime otpCreatedAt;

    /** Wrong guesses against the current code; the code dies after 5. */
    @Column(nullable = false)
    private int attempts = 0;

    @Column(length = 64)
    private String verificationToken;

    private LocalDateTime verificationTokenExpiry;

    @Column(length = 1, nullable = false)
    private String verified = "N";

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
