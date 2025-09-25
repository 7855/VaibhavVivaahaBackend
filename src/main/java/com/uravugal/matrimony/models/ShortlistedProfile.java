package com.uravugal.matrimony.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Table(name = "shortlisted_profiles")
@Data
@EqualsAndHashCode(callSuper = true)
public class ShortlistedProfile extends GenericEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shortlistedBy")
    private Long shortlistedBy; // Current user ID

    @Column(name = "shortlistedUserId")
    private Long shortlistedUserId; // Profile they liked

    @Column(name = "note")
    private String note; // Optional comment or note

}

