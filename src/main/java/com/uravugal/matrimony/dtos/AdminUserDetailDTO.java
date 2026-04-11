package com.uravugal.matrimony.dtos;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AdminUserDetailDTO {

    // Personal Details
    private Long userId;
    private String firstName;
    private String lastName;
    private String gender;
    private LocalDate dob;
    private String age;
    private String weight;
    private String fatherName;
    private String motherName;
    private String motherLanguage;
    private String profileCreatedFor;
    private String userStatus;

    // Contact & Location
    private String email;
    private String mobile;
    private String location;
    private String placeOfBirth;
    private String pin;

    // Religious Details
    private Integer casteId;
    private String casteName;
    private String star;
    private String dosham;

    // Professional Details
    private String degree;
    private String educationInDetail;
    private String employedAt;
    private String occupation;
    private String jobPlace;
    private String annualIncome;

    // Images
    private String profileImage;
    private String horoscope;
}
