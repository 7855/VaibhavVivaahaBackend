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
 * Records mobile/email identifiers of banned users so they cannot re-register
 * with the same credentials. Populated when admin issues a permanent BAN action
 * on a user report.
 */
@Entity
@Table(name = "banned_identifiers")
@Data
@EqualsAndHashCode(callSuper = true)
public class BannedIdentifier extends GenericEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The banned user's userId at the time of ban — for audit */
    @Column(name = "bannedUserId")
    private Long bannedUserId;

    @Column(name = "mobile", length = 20)
    private String mobile;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "banned_by_admin_id")
    private Long bannedByAdminId;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "banned_at")
    private java.time.LocalDateTime bannedAt;
}