package com.uravugal.matrimony.models;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.uravugal.matrimony.enums.ApprovalStatus;
import com.uravugal.matrimony.enums.Gender;
import com.uravugal.matrimony.enums.IsUser;

@Data
@Entity
@Table(name = "users")
public class UserEntity extends GenericEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "userId")
    private Long userId;

    @Column(name = "memberId", unique = true)
    private String memberId; // e.g., "UAD000372"

    @Column(name = "firstName")
    private String firstName;

    @Column(name = "lastName")
    private String lastName;

    @Column(name = "email")
    private String email;

    @Column(name = "mobile")
    private String mobile;

    @JsonIgnore
    @Column(name = "password")
    private String password;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "pin")
    private String pin;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender")
    private Gender gender;

    @Column(name = "dob")
    private LocalDate dob;

    @Column(name = "age")
    private String age;

    @Column(name = "casteId")
    private Integer casteId;

    /** Optional — always belongs to the caste referenced by casteId. Null = not specified. */
    @Column(name = "subcasteId")
    private Long subcasteId;

    /**
     * Resolved display name for subcasteId. Not persisted — populated on read by the profile
     * endpoints so the app doesn't need a second lookup just to render the subcaste.
     */
    @Transient
    private String subcasteName;

    /**
     * True while the member has a running profile boost. Not persisted — set by the list
     * endpoints that order boosted members first, so the card can show the Boosted ribbon.
     * Null (absent from JSON) on every endpoint that does not compute it.
     */
    @Transient
    private Boolean hasActiveBoost;

    @Column(name = "location")
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "userStatus")
    private ApprovalStatus userStatus;

    @Column(name = "suspended_until")
    private java.time.LocalDateTime suspendedUntil;

    @Column(name = "isBlocked")
    private char isBlocked;

    @Column(name = "profileImage")
    private String profileImage;

    @Column(name = "thumbnailImage")
    private String thumbnailImage;

    @Enumerated(EnumType.STRING)
    @Column(name = "isUser")
    private IsUser isUser;

    @JsonIgnore
    @Column(name = "otp")
    private Integer otp;

    @JsonIgnore
    @Column(name = "otp_created_at")
    private LocalDateTime otpCreatedAt;

    @Column(name = "view_count")
    private Integer viewCount = 0;
    
    @Column(name = "lastSeen")
    private LocalDateTime lastSeen;
    
    
    @Column(name = "isOnline")
    private boolean isOnline;
    
    @Column(name = "profileCreated")
    private String profileCreated;

    @JsonIgnore
    @Column(name = "refresh_token", length = 500)
    private String refreshToken;

    @JsonIgnore
    @Column(name = "refresh_token_expiry")
    private LocalDateTime refreshTokenExpiry;

    @JsonIgnore
    @Column(name = "reset_token", length = 100)
    private String resetToken;

    @JsonIgnore
    @Column(name = "reset_token_expiry")
    private LocalDateTime resetTokenExpiry;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    /**
     * Optional message the member writes to the admin when resubmitting a
     * REJECTED profile (max 250 chars, enforced server-side in
     * UserService.resubmitProfile). Shown on the admin verification queue.
     */
    @Column(name = "resubmission_note", length = 250)
    private String resubmissionNote;

    /**
     * Website quick-register leads (users rows with userStatus NULL): the
     * advisor's follow-up state — NEW | CONTACTED | CONVERTED | DROPPED —
     * and whether the lead's mobile was verified on WhatsApp on the website.
     * CONVERTED is set automatically when the lead registers in the app.
     */
    @Column(length = 20)
    private String leadStatus;

    @Column(length = 1)
    private String leadVerified;

    @Column(length = 500)
    private String leadNote;

    /**
     * New app registration flow (2026-10-19): the row is created at WhatsApp verification with
     * registrationStage = INCOMPLETE (userStatus stays NULL, so it is hidden like a website lead),
     * every step is saved into registrationDraft (JSON), and the LAST step turns it into a real
     * PENDING account (stage COMPLETE). registrationToken authorises step/complete calls and is
     * re-minted on every WhatsApp verification. Rows from the old sign-up have all three NULL.
     */
    @Column(length = 20)
    private String registrationStage;

    @Column(length = 64)
    private String registrationToken;

    @Column(columnDefinition = "TEXT")
    private String registrationDraft;

    /**
     * F8 — flipped to true when admin approves an IncomeVerification submission.
     * Surfaces the 💼 Salary Verified badge on the user's profile.
     */
    @Column(name = "income_verified")
    private Boolean incomeVerified = false;

    /** Flipped to true when admin approves an EducationVerification submission. */
    @Column(name = "education_verified")
    private Boolean educationVerified = false;

    /** Flipped to true when admin approves an IdVerification submission. */
    @Column(name = "id_verified")
    private Boolean idVerified = false;

    @OneToMany
    @JoinColumn(name = "userId", referencedColumnName = "userId")
    List<UserDetailEntity> userDetail;

    
    @PrePersist protected void prePersist() {
        super.prePersist(); // ensure createdAt/updatedAt are set from GenericEntity
        lastSeen = LocalDateTime.now();
    }
} 
