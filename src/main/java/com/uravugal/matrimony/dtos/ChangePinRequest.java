package com.uravugal.matrimony.dtos;

import lombok.Data;

@Data
public class ChangePinRequest {
    private String mobileNumber;
    private String pin;
}
