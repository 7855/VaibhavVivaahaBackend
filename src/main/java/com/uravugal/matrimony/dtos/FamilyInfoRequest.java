package com.uravugal.matrimony.dtos;

import lombok.Data;

@Data
public class FamilyInfoRequest {
    private String userId;
    private String familyType;
    private String familyStatus;
    private String fatherName;
    private String fatherOccupation;
    private String motherName;
    private String motherOccupation;
    private String noOfSiblings;
    private String noOfBrothers;
    private String noOfSisters;
    private String house;
    private String noOfBrothersMarried;
    private String noOfSistersMarried;
}
