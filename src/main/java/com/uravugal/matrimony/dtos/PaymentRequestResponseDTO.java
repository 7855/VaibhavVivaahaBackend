package com.uravugal.matrimony.dtos;

import com.uravugal.matrimony.enums.PaymentRequestStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PaymentRequestResponseDTO {
    private Long id;
    private Long planId;
    private String utrNumber;
    private String screenshotUrl;
    private PaymentRequestStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime verifiedAt;
    private Long verifiedBy;
}
