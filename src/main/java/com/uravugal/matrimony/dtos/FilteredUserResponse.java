package com.uravugal.matrimony.dtos;

import com.uravugal.matrimony.enums.IsUser;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FilteredUserResponse {
    private String firstName;
    private String lastName;
    private String age;
    private String location;
    private String height;
    private String profileImage;
    private IsUser isUser;
    private String occupation;
    private String annualIncome;
    private Long userId;
    private Long subscriptionPlanId;
    private String subscriptionTitle;
    private String subscriptionTag;
    private Boolean idVerified;
    private Boolean educationVerified;
    private Boolean incomeVerified;
    private Boolean hasActiveBoost;
}
