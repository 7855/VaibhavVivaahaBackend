package com.uravugal.matrimony.enums;

public enum ApprovalStatus {
    PENDING("PENDING"),
    REJECTED("REJECTED"),
    APPROVED("ACCEPTED");
    
    private final String value;
    
    ApprovalStatus(String value) {
        this.value = value;
    }
    
    @Override
    public String toString() {
        return value;
    }
}
