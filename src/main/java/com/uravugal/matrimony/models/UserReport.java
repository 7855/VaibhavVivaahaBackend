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

    @PrePersist
    protected void onCreate() {
        reportedAt = LocalDateTime.now();
    }
}
