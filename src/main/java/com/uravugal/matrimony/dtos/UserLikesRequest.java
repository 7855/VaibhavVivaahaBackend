package com.uravugal.matrimony.dtos;

import lombok.Data;

@Data
public class UserLikesRequest {
    private String likedBy;
    private String likedTo;
}
