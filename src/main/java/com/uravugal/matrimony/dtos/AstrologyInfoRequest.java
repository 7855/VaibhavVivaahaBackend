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
    // Stored in astroInfo[0].birth_time beside star/moon_sign/dosham — it is astro data and is
    // read together with the horoscope. Free text ("10:45 PM"); no format is imposed.
    private String birthTime;
}