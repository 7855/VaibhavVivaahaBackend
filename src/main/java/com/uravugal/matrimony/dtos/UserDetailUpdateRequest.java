package com.uravugal.matrimony.dtos;

import lombok.Data;

@Data
public class UserDetailUpdateRequest {
    private Long userId;
    private String section; // familyInfo, astronomicInfo, basicInfo, hobbies, languages
    private String data; // JSON string for the section
}
