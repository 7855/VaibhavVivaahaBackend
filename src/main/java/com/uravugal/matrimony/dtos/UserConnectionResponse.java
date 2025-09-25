package com.uravugal.matrimony.dtos;

import com.uravugal.matrimony.enums.ConnectionStatus;
import lombok.Data;

@Data
public class UserConnectionResponse {
    private Long userId;
    private String memberId;
    private String firstName;
    private String lastName;
    private String profileImage;
    private ConnectionStatus status;
}
