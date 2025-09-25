package com.uravugal.matrimony.models;

import com.uravugal.matrimony.enums.ApprovalStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Table(name = "restricted_field_requests")
@Data
@EqualsAndHashCode(callSuper = true)
public class RestrictedFieldRequest extends GenericEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requestedBy")
    private Long requestedBy; // Who is asking

    @Column(name = "requestedTo")
    private Long requestedTo; // Whose info is requested

    @Column(name = "fieldType")
    private String fieldType; // e.g., "mobile", "star", etc.

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ApprovalStatus status = ApprovalStatus.PENDING; // Pending / Approved / Rejected
}

