package com.uravugal.matrimony.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.services.UserFeatureUsageService;

@RestController
@RequestMapping("/userFeatureUsage")    
public class UserFeatureUsageController {
    
    @Autowired
    private UserFeatureUsageService userFeatureUsageService;

    @PostMapping("/updateUsedCount/{userId}/{subscriptionId}/{featureId}")
    public ResultResponse updateUsedCount(@PathVariable Long userId, @PathVariable Long subscriptionId, @PathVariable Long featureId) {
        ResultResponse response = new ResultResponse();
        try {
            response = userFeatureUsageService.updateUsedCount(userId, subscriptionId, featureId);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Failed to update used count: " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;    
    }
}
