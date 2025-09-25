package com.uravugal.matrimony.dtos;

import java.util.List;

import lombok.Data;

@Data
public class ProfileCompletionResponse {
    private Integer percentage;
    private Integer totalFields;
    private Integer completedFields;
    private List<String> missingFields;

    public ProfileCompletionResponse(Integer percentage, Integer totalFields, Integer completedFields, List<String> missingFields) {
        this.percentage = percentage;
        this.totalFields = totalFields;
        this.completedFields = completedFields;
        this.missingFields = missingFields;
    }
}