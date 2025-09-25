package com.uravugal.matrimony.dtos;

import lombok.Data;

@Data
public class ReportUserRequest {
    private String reportedByUserId;
    private Long reportedUserId;
    private String reason;
}
