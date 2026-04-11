package com.uravugal.matrimony.dtos;

import lombok.Data;

@Data
public class AdminApprovePaymentRequestDTO {
    private Long paymentRequestId;
    private Long adminId;
}
