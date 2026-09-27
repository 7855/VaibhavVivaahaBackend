package com.uravugal.matrimony.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Snapshot of a member at the moment they permanently deleted their account,
 * plus the reason they gave. Written by UserService.deleteAccount AFTER the
 * wipe succeeds (the snapshot is captured before the rows are gone) — so a row
 * here means the deletion really happened. GenericEntity.createdAt IS the
 * deletion timestamp. This table is intentionally NOT in the deleteAccount
 * wipe list: it exists to survive the member.
 */
@Entity
@Table(name = "deleted_accounts", indexes = {
        @Index(name = "idx_deleted_user", columnList = "userId"),
        @Index(name = "idx_deleted_mobile", columnList = "mobile")
})
@Data
@EqualsAndHashCode(callSuper = true)
public class DeletedAccount extends GenericEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "userId")
    private Long userId;

    @Column(name = "memberId", length = 32)
    private String memberId;

    @Column(name = "firstName", length = 128)
    private String firstName;

    @Column(name = "lastName", length = 128)
    private String lastName;

    @Column(name = "gender", length = 8)
    private String gender;

    @Column(name = "mobile", length = 32)
    private String mobile;

    @Column(name = "email", length = 256)
    private String email;

    @Column(name = "casteId")
    private Integer casteId;

    /** Plan the member held when they deleted (latest subscription's title; "Free" if none). */
    @Column(name = "planTitle", length = 64)
    private String planTitle;

    /** The member's stated reason for leaving (optional, capped app-side at 250). */
    @Column(name = "reason", length = 500)
    private String reason;
}
