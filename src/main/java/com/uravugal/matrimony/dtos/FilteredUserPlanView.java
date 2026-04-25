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
    // Integer (1/0) because native SQL returns TINYINT, not Java Boolean.
    Integer getIdVerified();
    Integer getEducationVerified();
    Integer getIncomeVerified();

    // Boost — true if user has an active 24h boost right now.
    Integer getHasActiveBoost();
}

