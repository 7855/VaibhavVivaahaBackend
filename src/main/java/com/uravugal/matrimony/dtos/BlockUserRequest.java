package com.uravugal.matrimony.dtos;

import lombok.Data;

@Data
public class BlockUserRequest {
    private String blockedByUserId;  // base64 encoded ID
    private Long blockedUserId;      // target user ID
}
