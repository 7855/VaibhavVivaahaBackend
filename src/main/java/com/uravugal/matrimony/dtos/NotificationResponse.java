package com.uravugal.matrimony.dtos;

import com.uravugal.matrimony.enums.ActiveStatus;

import lombok.Data;

@Data
public class NotificationResponse {
    private Long notificationId;
    private String notificationCategory;
    private String title;
    private String message;
    private String timestamp;
    private ActiveStatus isRead;
    private String avatar;
    private String userName;
    private String userAge;
    private String userLocation;
    private Long senderId;
    private Long receiverId;
}