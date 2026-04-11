package com.uravugal.matrimony.models;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.fasterxml.jackson.annotation.JsonManagedReference;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "savedSearches")
@Data
public class SavedSearchesEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "search_name", nullable = false, length = 100)
    private String searchName;

    @Column(name = "is_active", columnDefinition = "ENUM('Y','N') DEFAULT 'Y'")
    private String isActive = "Y";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

@OneToMany(mappedBy = "savedSearch", cascade = CascadeType.ALL, orphanRemoval = true)
@JsonManagedReference
private List<SavedSearchFilterEntity> filters;
}