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

    // Everything a member can edit in the app should be editable here too.
    // hobbies is a JSON ARRAY string in user_details, exactly as the app writes it.
    private String hobbies;
    // Religious / astro — stored inside astroInfo[0]
    private String birthTime;
    private String moonSign;
    // basicInfo (a JSON OBJECT with snake_case keys)
    private String physicalStatus;
    private String maritalStatus;
    // user_details columns
    private String currentAddress;   // presentAddress
    private String nativePlace;      // permanentAddress
    // familyInfo (a JSON ARRAY whose [0] holds these snake_case keys)
    private String familyType;
    private String familyStatus;
    private String fatherOccupation;
    private String motherOccupation;
    private String noOfBrothers;
    private String brothersMarried;
    private String noOfSisters;
    private String sistersMarried;
    private Integer subcasteId;
    private String subcasteName;
    private String about;
    private String height;

    // Images
    private String profileImage;
    private String horoscope;
}
