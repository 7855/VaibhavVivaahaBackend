package com.uravugal.matrimony.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserLikesRequest {
    @NotBlank(message = "Liked by user ID is required")
    private String likedBy;

    @NotBlank(message = "Liked to user ID is required")
    private String likedTo;
}