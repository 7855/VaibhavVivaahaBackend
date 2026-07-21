package com.uravugal.matrimony.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EducationInfoRequest {
    @NotBlank(message = "User ID is required")
    private String userId;
    private String education;
    private String occupation;
    private String employedAt;
    private String annualIncome;
    private String jobPlace;
    private String educationInDetail;
}