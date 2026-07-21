package com.uravugal.matrimony.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PersonalInfoRequest {
    @NotBlank(message = "User ID is required")
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
    private String currentAddress;
    private String nativePlace;
}