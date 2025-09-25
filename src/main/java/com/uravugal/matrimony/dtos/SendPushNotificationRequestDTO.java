package com.uravugal.matrimony.dtos;

import lombok.Data;

@Data
public class SendPushNotificationRequestDTO {
    private String userId;
    private String title;
    private String body;
}
