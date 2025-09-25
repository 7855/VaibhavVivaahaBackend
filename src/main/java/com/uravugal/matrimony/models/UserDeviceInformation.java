package com.uravugal.matrimony.models;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Date;

import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Data;

@Data
@Entity
@Table(name = "UserDeviceInformation")
public class UserDeviceInformation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id; 

    @Column(name = "userId")
    private Long userId;

    @Column(name = "deviceId")
    private String deviceId;

    @Column(name = "deviceType")
    private String deviceType;

    @Column(name = "deviceModel")
    private String deviceModel;

    @Column(name = "appVersion")
    private String appVersion;

    @Column(name = "osVersion")
    private String osVersion;

    @Column(name = "fcmToken", unique = true)
    private String fcmToken;
    
    @Column(name = "updatedAt")
    private Date updatedAt;

        
    @PrePersist
    protected void onCreate() {
        updatedAt = new Date();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = new Date();
    }
}
