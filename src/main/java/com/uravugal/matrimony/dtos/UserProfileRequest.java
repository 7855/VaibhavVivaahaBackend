package com.uravugal.matrimony.dtos;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserProfileRequest {
    private String userId; // Base64 encoded

    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name must not exceed 100 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name must not exceed 100 characters")
    private String lastName;

    @NotNull(message = "Date of birth is required")
    private LocalDate dateOfBirth;

    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "^[0-9]{10}$", message = "Mobile number must be 10 digits")
    private String mobileNumber;

    private String fathersName;
    private String mothersName;
    private String fathersOccupation;
    private String mothersOccupation;
    private String education;
    private String educationInDetail;
    private String occupation;
    private String jobPlace;
    private String employingIn;
    private String nativePlace;
    private String currentAddress;
    private String annualIncome;

    @NotBlank(message = "Gender is required")
    private String gender;

    @NotNull(message = "Caste is required")
    private Integer casteId;

    @NotBlank(message = "PIN is required")
    @Size(min = 4, max = 6, message = "PIN must be 4-6 digits")
    private String pin;

    private String location;
    private String age;
    private String email;

    // Email verification token from /auth/verify-otp with purpose=registration
    private String verificationToken;
}