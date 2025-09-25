package com.uravugal.matrimony.dtos;

import lombok.Data;

@Data
public class EducationInfoRequest {
    private String userId;
    private String education;
    private String occupation;
    private String employedAt;
    private String annualIncome;
}
