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
    // astroInfo[0].lagnam — the ascendant (rising sign at birth), one of the 12 rasi names.
    // Different from moonSign (the Moon's sign); not used by the 10-porutham match. Null = the
    // caller did not send it (older app), and the stored value is left alone.
    private String lagnam;
}