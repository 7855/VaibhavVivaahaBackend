package com.uravugal.matrimony.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReportUserRequest {
    @NotBlank(message = "Reporter user ID is required")
    private String reportedByUserId;

    @NotNull(message = "Reported user ID is required")
    private Long reportedUserId;

    @NotBlank(message = "Reason is required")
    private String reason;
}