package com.uravugal.matrimony.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BlockUserRequest {
    @NotBlank(message = "Blocker user ID is required")
    private String blockedByUserId;  // base64 encoded ID

    @NotNull(message = "Blocked user ID is required")
    private Long blockedUserId;      // target user ID
}