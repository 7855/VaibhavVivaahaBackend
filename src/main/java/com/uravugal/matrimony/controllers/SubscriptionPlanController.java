package com.uravugal.matrimony.controllers;

import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.SubscriptionPlan;
import com.uravugal.matrimony.services.SubscriptionPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/subscriptionPlans")
@RequiredArgsConstructor
public class SubscriptionPlanController {
    private final SubscriptionPlanService planService;

    @GetMapping("/getAllActivePlans")
    public ResultResponse getAllPlans() {
        ResultResponse response = new ResultResponse();
        try {
            response = planService.getAllActivePlans();
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Failed to fetch subscription plans: " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    @GetMapping("/getPopularPlans")
    public ResultResponse getPopularPlans() {
        ResultResponse response = new ResultResponse();
        try {
            response = planService.getPopularPlans();
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Failed to fetch popular plans: " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    @PostMapping("/createPlan")
    public ResultResponse createPlan(@RequestBody SubscriptionPlan plan) {
        ResultResponse response = new ResultResponse();
        try {
            response = planService.createPlan(plan);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Failed to create subscription plan: " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    @PutMapping("/updatePlan/{id}")
    public ResultResponse updatePlan(
            @PathVariable Long id,
            @RequestBody SubscriptionPlan plan) {
        ResultResponse response = new ResultResponse();
        try {
            response = planService.updatePlan(id, plan);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Failed to update subscription plan: " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    @DeleteMapping("/deletePlan/{id}")
    public ResultResponse deletePlan(@PathVariable Long id) {
        ResultResponse response = new ResultResponse();
        try {
            response = planService.deletePlan(id);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Failed to delete subscription plan: " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }
}