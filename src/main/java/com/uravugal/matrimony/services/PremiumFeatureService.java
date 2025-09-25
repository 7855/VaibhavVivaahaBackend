package com.uravugal.matrimony.services;

import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ActiveStatus;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.PremiumFeature;
import com.uravugal.matrimony.repositories.PremiumFeatureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class PremiumFeatureService {
    private final PremiumFeatureRepository premiumFeatureRepository;

    public ResultResponse getAllActiveFeatures() {
        ResultResponse response = new ResultResponse();
        try {
            List<PremiumFeature> features = premiumFeatureRepository.findByIsActiveOrderByDisplayOrderAsc(ActiveStatus.Y);
            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Premium features fetched successfully");
            response.setData(features);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching premium features: " + e.getMessage());
        }
        return response;
    }

    public ResultResponse createFeature(PremiumFeature feature) {
        ResultResponse response = new ResultResponse();
        try {
            // Set default active status if not set
            if (feature.getIsActive() == null) {
                feature.setIsActive(ActiveStatus.Y);
            }
            PremiumFeature savedFeature = premiumFeatureRepository.save(feature);
            
            response.setCode(201);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Premium feature created successfully");
            response.setData(savedFeature);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error creating premium feature: " + e.getMessage());
        }
        return response;
    }

    public ResultResponse updateFeature(Long id, PremiumFeature feature) {
        ResultResponse response = new ResultResponse();
        try {
            PremiumFeature existingFeature = premiumFeatureRepository.findById(id)
                    .orElseThrow(() -> new NoSuchElementException("Premium feature not found with id: " + id));
            
            // Update only non-null fields from the input
            if (feature.getTitle() != null) existingFeature.setTitle(feature.getTitle());
            if (feature.getIcon() != null) existingFeature.setIcon(feature.getIcon());
            if (feature.getDescription() != null) existingFeature.setDescription(feature.getDescription());
            if (feature.getBgColor() != null) existingFeature.setBgColor(feature.getBgColor());
            if (feature.getTextColor() != null) existingFeature.setTextColor(feature.getTextColor());
            if (feature.getDisplayOrder() != null) existingFeature.setDisplayOrder(feature.getDisplayOrder());
            if (feature.getIsActive() != null) existingFeature.setActive(feature.getActive());
            
            PremiumFeature updatedFeature = premiumFeatureRepository.save(existingFeature);
            
            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Premium feature updated successfully");
            response.setData(updatedFeature);
        } catch (NoSuchElementException e) {
            response.setCode(404);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage(e.getMessage());
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error updating premium feature: " + e.getMessage());
        }
        return response;
    }

    public ResultResponse deleteFeature(Long id) {
        ResultResponse response = new ResultResponse();
        try {
            PremiumFeature feature = premiumFeatureRepository.findById(id)
                    .orElseThrow(() -> new NoSuchElementException("Premium feature not found with id: " + id));
            
            // Soft delete
            feature.setActive(ActiveStatus.N);
            premiumFeatureRepository.save(feature);
            
            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Premium feature deleted successfully");
        } catch (NoSuchElementException e) {
            response.setCode(404);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage(e.getMessage());
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error deleting premium feature: " + e.getMessage());
        }
        return response;
    }
}