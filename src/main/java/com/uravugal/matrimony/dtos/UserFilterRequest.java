package com.uravugal.matrimony.dtos;

import java.util.List;

import lombok.Data;

@Data
public class UserFilterRequest {

    private String minAge;
    private String maxAge;
    private String minAnnualIncome;
    private String maxAnnualIncome;

    private List<String> occupation;
    private String location;

    private List<String> employedAt;
    private List<String> degree;
    private List<String> star;
    private List<String> dosham;

    private String profileImageStatus;
    private String profilesWithHoroscope;

    private Integer casteId;
    private String gender;
    private Long userId;
}

