package com.uravugal.matrimony.dtos;

import java.time.LocalDate;

import lombok.Data;

@Data
public class UserProfileRequest {
    private String userId; // Base64 encoded
    private String firstName;
    private String lastName;
    private LocalDate dateOfBirth;
    private String mobileNumber;
    private String fathersName;
    private String mothersName;
    private String fathersOccupation;
    private String mothersOccupation;
    private String education;
    private String occupation;
    private String jobPlace;
    private String employingIn;
    private String nativePlace;
    private String currentAddress;
    private String annualIncome;
    private String gender;
    private Integer casteId;
    private String pin;
    private String location;
    private String age;
    private String email;
}
