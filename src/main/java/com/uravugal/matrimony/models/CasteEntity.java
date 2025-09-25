package com.uravugal.matrimony.models;

import com.uravugal.matrimony.enums.caste;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Table(name = "castes")
@Data
@EqualsAndHashCode(callSuper = true)
public class CasteEntity extends GenericEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="casteName", length = 100, nullable = false)
    private String casteName;

    @Enumerated(EnumType.STRING)
    @Column(name = "casteCode")
    private caste casteCode;

}
