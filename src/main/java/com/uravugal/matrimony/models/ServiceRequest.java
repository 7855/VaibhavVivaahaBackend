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
@Table(name = "service_requests")
@Data
@EqualsAndHashCode(callSuper = true)
public class ServiceRequest extends GenericEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "userId", nullable = false)
    private Long userId;

    /**
     * The other user this request is about (e.g. the profile the caller
     * wants to ring). Nullable for request types that aren't tied to
     * another member (DEDICATED_RM, FAMILY_ASSISTED_MATCH, etc.).
     */
    @Column(name = "target_user_id")
    private Long targetUserId;

    /**
     * Feature code that drives gating + admin routing.
     * One of: VOICE_CALL, VIDEO_PROFILE, FAMILY_LOGIN, SPEAK_FAMILY,
     * DEDICATED_RM, FAMILY_ASSISTED_MATCH
     */
    @Column(name = "request_type", length = 64, nullable = false)
    private String requestType;

    /** PENDING, IN_PROGRESS, DONE, REJECTED */
    @Column(name = "status", length = 32, nullable = false)
    private String status = "PENDING";

    @Column(name = "note", length = 1000)
    private String note;

    @Column(name = "assigned_admin_id")
    private Long assignedAdminId;
}
