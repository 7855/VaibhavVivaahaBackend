package com.uravugal.matrimony.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Entity
@Table(name = "profile_boosts")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProfileBoost extends GenericEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "userId", nullable = false)
    private Long userId;

    @Column(name = "startedAt", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "expiresAt", nullable = false)
    private LocalDateTime expiresAt;

    /** MONTHLY_CREDIT | PURCHASED */
    @Column(name = "source", length = 30, nullable = false)
    private String source;

    /** ACTIVE | EXPIRED */
    @Column(name = "status", length = 20, nullable = false)
    private String status = "ACTIVE";
}