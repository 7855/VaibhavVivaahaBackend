package com.uravugal.matrimony.dtos;

import java.util.Date;

import lombok.Data;

@Data
public class AdminUserListDTO {
    private Long userId;
    private String username;
    private String profileId;
    private String location;
    private String casteName;
    private Integer profileCompletedPercentage;
    private String subscriptionPlan;
    private String approvalStatus;
    private Date createdOn;
    private String isActive;

    public AdminUserListDTO(Long userId, String username, String profileId, String location,
            String casteName, Integer profileCompletedPercentage,
            String subscriptionPlan, String approvalStatus, Date createdOn, String isActive) {
        this.userId = userId;
        this.username = username;
        this.profileId = profileId;
        this.location = location;
        this.casteName = casteName;
        this.profileCompletedPercentage = profileCompletedPercentage;
        this.subscriptionPlan = subscriptionPlan;
        this.approvalStatus = approvalStatus;
        this.createdOn = createdOn;
        this.isActive = isActive;
    }
}
