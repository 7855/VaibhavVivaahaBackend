package com.uravugal.matrimony.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * A subcaste always belongs to exactly one caste (see castes.id). Unlike CasteEntity there is
 * deliberately NO enum code column here — the subcaste name is a plain String so new values can
 * be added by data alone, without a code change/redeploy.
 */
@Entity
@Table(name = "subcastes")
@Data
@EqualsAndHashCode(callSuper = true)
public class SubCasteEntity extends GenericEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "casteId", nullable = false)
    private Long casteId;

    @Column(name = "subcasteName", length = 100, nullable = false)
    private String subcasteName;

}
