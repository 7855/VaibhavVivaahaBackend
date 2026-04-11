package com.uravugal.matrimony.models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "userReports")
public class UserReport extends GenericEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reportedByUserId")
    private Long reportedByUserId;

    @Column(name = "reportedUserId")
    private Long reportedUserId;

    @Column(name = "reason", length = 1000)
    private String reason;

    @Column(name = "reportedAt")
    private LocalDateTime reportedAt;

    /** PENDING, DISMISSED, WARNED, SUSPENDED, BANNED */
    @Column(name = "status", length = 32)
    private String status = "PENDING";

    @Column(name = "reviewed_by_admin_id")
    private Long reviewedByAdminId;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    @PrePersist
    protected void onCreate() {
        reportedAt = LocalDateTime.now();
        if (status == null) status = "PENDING";
    }
}
