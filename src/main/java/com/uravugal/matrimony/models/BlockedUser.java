package com.uravugal.matrimony.models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "blockedUsers")
public class BlockedUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "blockedByUserId")
    private Long blockedByUserId;

    @Column(name = "blockedUserId")
    private Long blockedUserId;

    @Column(name = "blockedAt")
    private LocalDateTime blockedAt;

    @PrePersist
    protected void onCreate() {
        blockedAt = LocalDateTime.now();
    }
}
