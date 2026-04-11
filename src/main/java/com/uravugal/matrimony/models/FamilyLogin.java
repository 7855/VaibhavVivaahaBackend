package com.uravugal.matrimony.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * A parent / family member login tied to a primary user's account.
 * When a FamilyLogin logs in, the returned session is scoped to the
 * primary user's data — parent never gets their own profile.
 */
@Entity
@Table(name = "family_logins")
@Data
@EqualsAndHashCode(callSuper = true)
public class FamilyLogin extends GenericEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The matrimony user whose profile the parent can access */
    @Column(name = "primaryUserId", nullable = false)
    private Long primaryUserId;

    @Column(name = "parentName", length = 120, nullable = false)
    private String parentName;

    /** e.g. 'Father', 'Mother', 'Brother', 'Sister', 'Uncle' */
    @Column(name = "relationship", length = 50)
    private String relationship;

    @Column(name = "mobile", length = 20, nullable = false, unique = true)
    private String mobile;

    @Column(name = "email", length = 120)
    private String email;

    /** Base64-encoded PIN (matches the existing UserEntity.pin scheme) */
    @Column(name = "pin", length = 255, nullable = false)
    private String pin;

    /** ACTIVE, REVOKED */
    @Column(name = "status", length = 20, nullable = false)
    private String status = "ACTIVE";

    @Column(name = "last_login_at")
    private java.time.LocalDateTime lastLoginAt;
}