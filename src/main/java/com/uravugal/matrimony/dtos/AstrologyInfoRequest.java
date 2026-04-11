package com.uravugal.matrimony.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AstrologyInfoRequest {
    @NotBlank(message = "User ID is required")
    private String userId;
    private String caste;
    private String star;
    private String moonSign;
    private String dosham;
    private String sunSign;
}