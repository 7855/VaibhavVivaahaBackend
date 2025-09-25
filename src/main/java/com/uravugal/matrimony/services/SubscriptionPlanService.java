package com.uravugal.matrimony.services;

import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ActiveStatus;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.SubscriptionPlan;
import com.uravugal.matrimony.repositories.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class SubscriptionPlanService {
    private final SubscriptionPlanRepository planRepository;

    public ResultResponse getAllActivePlans() {
        ResultResponse response = new ResultResponse();
        try {
            List<SubscriptionPlan> plans = planRepository.findByIsActiveTrueOrderByPriceAsc();
            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Subscription plans fetched successfully");
            response.setData(plans);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching subscription plans: " + e.getMessage());
        }
        return response;
    }

    public ResultResponse getPopularPlans() {
        ResultResponse response = new ResultResponse();
        try {
            List<SubscriptionPlan> popularPlans = planRepository.findByIsActiveTrueAndIsPopularTrueOrderByPriceAsc();
            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Popular subscription plans fetched successfully");
            response.setData(popularPlans);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching popular subscription plans: " + e.getMessage());
        }
        return response;
    }

    public ResultResponse createPlan(SubscriptionPlan plan) {
        ResultResponse response = new ResultResponse();
        try {
            // Set default active status if not set
            if (plan.getIsActive() == null) {
                plan.setIsActive(ActiveStatus.Y);
            }
            SubscriptionPlan savedPlan = planRepository.save(plan);
            
            response.setCode(201);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Subscription plan created successfully");
            response.setData(savedPlan);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error creating subscription plan: " + e.getMessage());
        }
        return response;
    }

    public ResultResponse updatePlan(Long id, SubscriptionPlan plan) {
        ResultResponse response = new ResultResponse();
        try {
            SubscriptionPlan existingPlan = planRepository.findById(id)
                    .orElseThrow(() -> new NoSuchElementException("Subscription plan not found with id: " + id));
            
            // Update only non-null fields from the input
            if (plan.getTitle() != null) existingPlan.setTitle(plan.getTitle());
            if (plan.getPeriod() != null) existingPlan.setPeriod(plan.getPeriod());
            if (plan.getPrice() != null) existingPlan.setPrice(plan.getPrice());
            if (plan.getOriginalPrice() != null) existingPlan.setOriginalPrice(plan.getOriginalPrice());
            if (plan.getDiscount() != null) existingPlan.setDiscount(plan.getDiscount());
            if (plan.getSavings() != null) existingPlan.setSavings(plan.getSavings());
            if (plan.getDurationDays() != null) existingPlan.setDurationDays(plan.getDurationDays());
            if (plan.getIsPopular() != null) existingPlan.setIsPopular(plan.getIsPopular());
            if (plan.getIsActive() != null) existingPlan.setIsActive(plan.getIsActive());
            
            SubscriptionPlan updatedPlan = planRepository.save(existingPlan);
            
            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Subscription plan updated successfully");
            response.setData(updatedPlan);
        } catch (NoSuchElementException e) {
            response.setCode(404);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage(e.getMessage());
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error updating subscription plan: " + e.getMessage());
        }
        return response;
    }

    public ResultResponse deletePlan(Long id) {
        ResultResponse response = new ResultResponse();
        try {
            SubscriptionPlan plan = planRepository.findById(id)
                    .orElseThrow(() -> new NoSuchElementException("Subscription plan not found with id: " + id));
            
            // Soft delete
            plan.setIsActive(ActiveStatus.N);
            planRepository.save(plan);
            
            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Subscription plan deleted successfully");
        } catch (NoSuchElementException e) {
            response.setCode(404);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage(e.getMessage());
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error deleting subscription plan: " + e.getMessage());
        }
        return response;
    }
}