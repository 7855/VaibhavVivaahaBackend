package com.uravugal.matrimony.dtos;

import lombok.Data;

@Data
public class UserFilterRequest {
    private String minAge;  // Changed from Integer to String to match UserEntity
    private String maxAge;  // Changed from Integer to String to match UserEntity
    private String minAnnualIncome;  // Changed from minSalary to minAnnualIncome
    private String maxAnnualIncome;  // Changed from maxSalary to maxAnnualIncome
    private String occupation;  // Changed from degree to occupation
    private String location;
    private String employedAt;  // Changed from employedAt to jobPlace
    private String profileImageStatus; // Y for with profile image, N for all
    private Integer casteId;
    private String gender;
}
