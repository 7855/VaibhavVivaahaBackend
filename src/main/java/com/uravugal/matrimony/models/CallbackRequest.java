package com.uravugal.matrimony.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Table(name = "callback_requests", indexes = {
        @Index(name = "idx_callback_mobile", columnList = "mobile"),
        @Index(name = "idx_callback_user", columnList = "userId"),
        @Index(name = "idx_callback_status", columnList = "status")
})
@Data
@EqualsAndHashCode(callSuper = true)
public class CallbackRequest extends GenericEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "userId")
    private Long userId;

    @Column(name = "name", length = 128, nullable = false)
    private String name;

    @Column(name = "mobile", length = 20, nullable = false)
    private String mobile;

    @Column(name = "email", length = 128)
    private String email;

    @Column(name = "plan_interested", length = 64)
    private String planInterested;

    @Column(name = "note", length = 1000)
    private String note;

    @Column(name = "best_time_to_call", length = 32)
    private String bestTimeToCall;

    /** NEW, CONTACTED, CONVERTED, CLOSED */
    @Column(name = "status", length = 32, nullable = false)
    private String status = "NEW";

    @Column(name = "assigned_admin_id")
    private Long assignedAdminId;
}