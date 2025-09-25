package com.uravugal.matrimony.controllers;

import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.PremiumFeature;
import com.uravugal.matrimony.services.PremiumFeatureService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/premiumFeatures")
@RequiredArgsConstructor
public class PremiumFeatureController {
    private final PremiumFeatureService featureService;

    @GetMapping("/getAllActiveFeatures")
    public ResultResponse getAllFeatures() {
        ResultResponse response = new ResultResponse();
        try {
            response = featureService.getAllActiveFeatures();
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Failed to fetch premium features: " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    @PostMapping("/createFeature")
    public ResultResponse createFeature(@RequestBody PremiumFeature feature) {
        ResultResponse response = new ResultResponse();
        try {
            response = featureService.createFeature(feature);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Failed to create premium feature: " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    @PutMapping("/updateFeature/{id}")
    public ResultResponse updateFeature(
            @PathVariable Long id,
            @RequestBody PremiumFeature feature) {
        ResultResponse response = new ResultResponse();
        try {
            response = featureService.updateFeature(id, feature);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Failed to update premium feature: " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    @DeleteMapping("/deleteFeature/{id}")
    public ResultResponse deleteFeature(@PathVariable Long id) {
        ResultResponse response = new ResultResponse();
        try {
            response = featureService.deleteFeature(id);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Failed to delete premium feature: " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }
}