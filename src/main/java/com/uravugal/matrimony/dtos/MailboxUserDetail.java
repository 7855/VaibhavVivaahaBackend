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

    // Verification flags — surfaced as shield next to name on mailbox rows
    private Boolean idVerified;
    private Boolean educationVerified;
    private Boolean incomeVerified;

    // Interest request status for Sent tab display
    private String acceptStatus;
}
