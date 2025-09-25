package com.uravugal.matrimony.dtos;

import lombok.Data;

@Data
public class PersonalInfoRequest {
    private String userId;
    private String firstName;
    private String lastName;
    private String dateOfBirth;
    private String height;
    private String weight;
    private String physicalStatus;
    private String maritalStatus;
    private String motherLanguage;
    private String placeOfBirth;
    private String numberOfChildren;
}
