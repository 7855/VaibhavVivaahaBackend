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
