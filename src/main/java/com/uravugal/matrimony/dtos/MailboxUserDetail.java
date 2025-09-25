package com.uravugal.matrimony.dtos;

import lombok.Data;

@Data
public class MailboxUserDetail {
    private Long userId;
    private String firstName;
    private String lastName;
    private Integer age;
    private String degree;
    private String annualIncome;
    private String occupation;
    private String location;
    private String profileImage;
    private Long interestId;
    private Long shortlistedId;
}
