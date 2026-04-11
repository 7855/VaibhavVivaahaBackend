package com.uravugal.matrimony.models;
import com.fasterxml.jackson.annotation.JsonBackReference;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "savedSearchFilters")
@Data
public class SavedSearchFilterEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "saved_search_id", nullable = false)
@JsonBackReference
private SavedSearchesEntity savedSearch;

    @Column(name = "filter_key", nullable = false, length = 50)
    private String filterKey;
    
    @Column(name = "filter_value", nullable = false, length = 255)
    private String filterValue;
}
