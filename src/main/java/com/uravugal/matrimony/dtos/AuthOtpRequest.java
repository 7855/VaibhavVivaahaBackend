package com.uravugal.matrimony.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AuthOtpRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Please enter a valid email")
    private String email;

    @NotBlank(message = "Purpose is required")
    private String purpose; // "registration" or "reset"

    private String firstName;
}