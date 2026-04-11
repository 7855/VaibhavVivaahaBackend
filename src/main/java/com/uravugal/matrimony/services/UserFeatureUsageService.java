package com.uravugal.matrimony.services;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.PlanFeatures;
import com.uravugal.matrimony.models.UserFeatureUsage;
import com.uravugal.matrimony.models.UserSubscriptions;
import com.uravugal.matrimony.repositories.PlanFeaturesRepository;
import com.uravugal.matrimony.repositories.UserFeatureUsageRepository;
import com.uravugal.matrimony.repositories.UserSubscriptionsRepository;

import jakarta.transaction.Transactional;

@Service
public class UserFeatureUsageService {

    @Autowired
    private UserFeatureUsageRepository userFeatureUsageRepository;

    @Autowired
    private UserSubscriptionsRepository userSubscriptionRepository;

    @Autowired
    private PlanFeaturesRepository planFeaturesRepository;

    public ResultResponse updateUsedCount(Long userId, Long subscriptionId, Long featureId) {
        ResultResponse response = new ResultResponse();
        try {
            Optional<UserFeatureUsage> userFeatureUsageOpt = userFeatureUsageRepository
                    .findByUserIdAndSubscriptionIdAndFeatureId(userId, subscriptionId, featureId);

            // Validate subscription
            Optional<UserSubscriptions> userSubscriptionsOpt = userSubscriptionRepository.findById(subscriptionId);
            if (userSubscriptionsOpt.isEmpty()) {
                response.setCode(404);
                response.setMessage("Subscription Not Found");
                response.setStatus(ResponseStatus.FAILURE);
                return response;
            }

            UserSubscriptions userSubscriptions = userSubscriptionsOpt.get();

            // Block free plan users
            if (userSubscriptions.getSubscriptionPlanId() == 1) {
                response.setCode(403);
                response.setMessage("Free plan users cannot use this feature");
                response.setStatus(ResponseStatus.FAILURE);
                return response;
            }

            System.out.println("featureId" + featureId);
            System.out.println("userSubscriptions.getSubscriptionPlanId()" + userSubscriptions.getSubscriptionPlanId());

            // Check feature limits for the plan
            PlanFeatures planFeatures = planFeaturesRepository.findByFeatureIdAndSubscriptionPlanId(
                    featureId,
                    userSubscriptions.getSubscriptionPlanId());

            if (planFeatures == null) {
                response.setCode(404);
                response.setMessage("Feature not available in this plan");
                response.setStatus(ResponseStatus.FAILURE);
                return response;
            }

            // Update used count if record exists
            if (userFeatureUsageOpt.isPresent()) {
                UserFeatureUsage usage = userFeatureUsageOpt.get();
                int limit = Integer.parseInt(planFeatures.getLimitValue());

                if (usage.getUsedCount() < limit) {
                    usage.setUsedCount(usage.getUsedCount() + 1);
                    userFeatureUsageRepository.save(usage);

                    response.setCode(200);
                    response.setMessage("Used count updated successfully");
                    response.setStatus(ResponseStatus.SUCCESS);
                } else {
                    response.setCode(401);
                    response.setMessage("Feature usage limit exceeded");
                    response.setStatus(ResponseStatus.FAILURE);
                }
            } else {
                // ⚠ No insert happens here
                response.setCode(404);
                response.setMessage("Feature usage record not found for this user");
                response.setStatus(ResponseStatus.FAILURE);
            }

        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Failed to update used count: " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    @Transactional
    public void validateAndIncrementUsage(
            Long userId,
            Long subscriptionId,
            Long featureId) {
        System.out.println("userId " + userId);
        System.out.println("subscriptionId " + subscriptionId);
        System.out.println("featureId " + featureId);

        UserSubscriptions subscription = userSubscriptionRepository
                .findById(subscriptionId)
                .orElseThrow(() -> new RuntimeException("SUBSCRIPTION_NOT_FOUND"));

        if (subscription.getSubscriptionPlanId() == 1) {
            throw new RuntimeException("FREE_PLAN_RESTRICTED");
        }

        System.out.println("beforr plan feature");
        PlanFeatures planFeature = planFeaturesRepository.findByFeatureIdAndSubscriptionPlanId(
                featureId,
                subscription.getSubscriptionPlanId());
        System.out.println("after plan feature" + planFeature);

        if (planFeature == null) {
            throw new RuntimeException("FEATURE_NOT_AVAILABLE");
        }

        int limit = Integer.parseInt(planFeature.getLimitValue());

        UserFeatureUsage usage = userFeatureUsageRepository
                .findByUserIdAndSubscriptionIdAndFeatureId(
                        userId, subscriptionId, featureId)
                .orElseGet(() -> {
                    UserFeatureUsage u = new UserFeatureUsage();
                    u.setUserId(userId);
                    u.setSubscriptionId(subscriptionId);
                    u.setFeatureId(featureId);
                    u.setUsedCount(0);
                    return u;
                });

        if (usage.getUsedCount() >= limit) {
            throw new RuntimeException("INTEREST_LIMIT_EXCEEDED");
        }

        usage.setUsedCount(usage.getUsedCount() + 1);
        userFeatureUsageRepository.save(usage);
        System.out.println("user used count added");
    }
}
