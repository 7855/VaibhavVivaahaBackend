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
    // Multi-select on the search screen (chips, same as degree/star/dosham). Null or empty
    // means "any subcaste" — see the COALESCE guard in UserRepository's native queries.
    private List<Long> subcasteIds;
    private String gender;
    private Long userId;
}

