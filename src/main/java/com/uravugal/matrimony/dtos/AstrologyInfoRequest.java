package com.uravugal.matrimony.dtos;

import lombok.Data;

@Data
public class AstrologyInfoRequest {
    private String userId;
    private String caste;
    private String star;
    private String moonSign;
    private String dosham;
    private String sunSign;
}
