package com.uravugal.matrimony.models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "contact_reveals")
@Data
public class ContactReveal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "viewerId", nullable = false)
    private Long viewerId;

    @Column(name = "revealedUserId", nullable = false)
    private Long revealedUserId;

    @Column(name = "createdAt")
    private LocalDateTime createdAt = LocalDateTime.now();
}