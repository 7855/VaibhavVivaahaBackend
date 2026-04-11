package com.uravugal.matrimony.dtos;

public interface FilteredUserPlanView {

    Long getUserId();
    String getFirstName();
    String getLastName();
    String getAge();
    String getLocation();
    String getHeight();
    String getProfileImage();
    String getOccupation();
    String getAnnualIncome();
    String getIsUser();

    Long getSubscriptionPlanId();
    String getSubscriptionTitle();

    // Verification flags — surfaced as small shield on result cards.
    Boolean getIdVerified();
    Boolean getEducationVerified();
    Boolean getIncomeVerified();
}

