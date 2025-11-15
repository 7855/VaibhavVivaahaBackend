package com.uravugal.matrimony.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.UserSubscriptions;
import com.uravugal.matrimony.services.UserSubscriptionsService;

@RestController
@RequestMapping("/userSubscriptions")
public class UserSubscriptionsController {

    @Autowired
    private UserSubscriptionsService userSubscriptionsService;
    
    @PostMapping("/createUserSubscription")
    public ResultResponse createUserSubscription(@RequestBody UserSubscriptions userSubscriptions) {
        ResultResponse response = new ResultResponse();
        try {
            System.out.println("userSubscriptions: " + userSubscriptions);
            response = userSubscriptionsService.createUserSubscription(userSubscriptions);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Failed to create user subscription: " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;    
    }

    @GetMapping("/getActiveUserSubscriptionByUserId/{userId}")
    public ResultResponse getActiveUserSubscriptionUserId(@PathVariable Long userId) {
        ResultResponse response = new ResultResponse();
        try {
            response = userSubscriptionsService.getActiveUserSubscriptionUserId(userId);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Failed to create user subscription: " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;    
    }

    @GetMapping("/getAllUserSubscriptionByUserId/{userId}")
    public ResultResponse getAllUserSubscriptionByUserId(@PathVariable Long userId) {
        ResultResponse response = new ResultResponse();
        try {
            response = userSubscriptionsService.getAllUserSubscriptionByUserId(userId);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Failed to create user subscription: " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;    
    }
}
