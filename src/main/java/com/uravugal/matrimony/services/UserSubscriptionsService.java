package com.uravugal.matrimony.services;

import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.enums.SubscriptionStatus;
import com.uravugal.matrimony.models.Features;
import com.uravugal.matrimony.models.PlanFeatures;
import com.uravugal.matrimony.models.SubscriptionPlan;
import com.uravugal.matrimony.models.UserFeatureUsage;
import com.uravugal.matrimony.models.UserSubscriptions;
import com.uravugal.matrimony.repositories.FeaturesRepository;
import com.uravugal.matrimony.repositories.PlanFeaturesRepository;
import com.uravugal.matrimony.repositories.SubscriptionPlanRepository;
import com.uravugal.matrimony.repositories.UserFeatureUsageRepository;
import com.uravugal.matrimony.repositories.UserSubscriptionsRepository;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserSubscriptionsService {

    @Autowired
    private UserSubscriptionsRepository userSubscriptionsRepository;

    @Autowired
    private PlanFeaturesRepository planFeaturesRepository;

    @Autowired
    private FeaturesRepository featuresRepository;

    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Autowired
    private UserFeatureUsageRepository userFeatureUsageRepository;

    public ResultResponse createUserSubscription(UserSubscriptions userSubscriptions) {
        ResultResponse response = new ResultResponse();
        try {
            UserSubscriptions savedSubscription = userSubscriptionsRepository.save(userSubscriptions);

            if (savedSubscription != null) {
                List<PlanFeatures> planFeatures = planFeaturesRepository
                        .findBySubscriptionPlanId(savedSubscription.getSubscriptionPlanId());

                if (!planFeatures.isEmpty()) {
                    Features sendRequestFeature = featuresRepository.findByCode("SEND_REQUEST");

                    if (sendRequestFeature != null) {
                        Optional<PlanFeatures> sendRequestPlanFeatureOpt = planFeatures.stream()
                                .filter(pf -> pf.getFeatureId().equals(sendRequestFeature.getId()))
                                .findFirst();

                        if (sendRequestPlanFeatureOpt.isPresent()) {
                            String limitValueStr = sendRequestPlanFeatureOpt.get().getLimitValue();

                            if (limitValueStr != null) {
                                try {
                                    int limitValue = Integer.parseInt(limitValueStr);
                                    if (!"-1".equals(limitValueStr) && limitValue > 0) {
                                        // Only now create or update usage record
                                        Optional<UserFeatureUsage> existingUsageOpt = userFeatureUsageRepository
                                                .findByUserIdAndFeatureId(savedSubscription.getUserId(),
                                                        sendRequestFeature.getId());

                                        UserFeatureUsage usage = existingUsageOpt.orElse(new UserFeatureUsage());
                                        usage.setUserId(savedSubscription.getUserId());
                                        usage.setFeatureId(sendRequestFeature.getId());
                                        usage.setSubscriptionId(savedSubscription.getId());
                                        usage.setUsedCount(0);
                                        usage.setLastResetDate(LocalDate.now());

                                        userFeatureUsageRepository.save(usage);
                                    }
                                } catch (NumberFormatException e) {
                                    // Non-numeric limitValue: -1, advanced, allowed → skip creating usage
                                }
                            }
                        }
                    }
                }

                response.setCode(200);
                response.setMessage("User subscription created successfully");
                response.setStatus(ResponseStatus.SUCCESS);
            }
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Failed to create user subscription: " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    public ResultResponse getActiveUserSubscriptionUserId(Long userId) {
        ResultResponse response = new ResultResponse();
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> entitlements = new HashMap<>();

        try {
            if (userId == null) {
                response.setCode(400);
                response.setMessage("User ID cannot be null");
                response.setStatus(ResponseStatus.FAILURE);
                return response;
            }

            // 1. Get active subscription for the user
            List<UserSubscriptions> userSubscriptions = userSubscriptionsRepository
                    .findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE);

            if (userSubscriptions == null || userSubscriptions.isEmpty()) {
                response.setCode(404);
                response.setMessage("No active subscription found for user");
                response.setStatus(ResponseStatus.FAILURE);
                return response;
            }

            UserSubscriptions userSubscription = userSubscriptions.get(0);

            // 2. Fetch the subscription plan
            Optional<SubscriptionPlan> subscriptionPlanOpt = subscriptionPlanRepository
                    .findById(userSubscription.getSubscriptionPlanId());

            if (!subscriptionPlanOpt.isPresent()) {
                response.setCode(404);
                response.setMessage("Subscription plan not found");
                response.setStatus(ResponseStatus.FAILURE);
                return response;
            }

            SubscriptionPlan subscriptionPlan = subscriptionPlanOpt.get();

            // 3. Basic subscription info
            data.put("planCode", subscriptionPlan.getTitle().toUpperCase().replace(" ", "_"));
            data.put("planTitle", subscriptionPlan.getTitle());
            data.put("startDate", userSubscription.getStartDate());
            data.put("endDate", userSubscription.getEndDate());
            data.put("status", userSubscription.getStatus().name());
            data.put("subscriptionId", userSubscription.getId());

            // 4. Initialize all features dynamically with defaults
            List<Features> allFeatures = featuresRepository.findAll();
            for (Features feature : allFeatures) {
                if (feature.getCode() != null) {
                    String featureCode = feature.getCode().toUpperCase();
                    if ("MAX_REQUESTS_DAY".equals(featureCode) || "SEND_REQUESTS".equals(featureCode)) {
                        entitlements.put(toCamelCase(featureCode), 0);
                    } else {
                        entitlements.put(toCamelCase(featureCode), false);
                    }
                }
            }

            // 5. Apply plan-specific feature overrides
            List<PlanFeatures> planFeatures = planFeaturesRepository
                    .findBySubscriptionPlanId(userSubscription.getSubscriptionPlanId());

            for (PlanFeatures pf : planFeatures) {

                Features feature = featuresRepository.findById(pf.getFeatureId()).orElse(null);
                if (feature == null)
                    continue;

                String key = toCamelCase(feature.getCode());
                String val = pf.getLimitValue();
                String period = pf.getLimitPeriod(); // DAY / MONTH / PLAN / null

                if (val == null || "enabled".equalsIgnoreCase(val)) {
                    entitlements.put(key, true);
                } else if ("unlimited".equalsIgnoreCase(val)) {
                    entitlements.put(key, "unlimited");
                } else if ("FULL".equalsIgnoreCase(val)) {
                    entitlements.put(key, "full");
                } else if ("LIMITED".equalsIgnoreCase(val)) {
                    entitlements.put(key, "limited");
                } else if (val.matches("\\d+")) {
                    Map<String, Object> quota = new HashMap<>();
                    quota.put("limit", Integer.parseInt(val));
                    quota.put("period", period == null ? "PLAN" : period);
                    entitlements.put(key, quota);
                }
            }

            // 6. Attach entitlements to response
            data.put("entitlements", entitlements);

            response.setCode(200);
            response.setData(data);
            response.setMessage("User subscription found");
            response.setStatus(ResponseStatus.SUCCESS);

        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Failed to get active user subscription: " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }

        return response;
    }

    /**
     * Convert UPPER_UNDERSCORE to lowerCamelCase
     */
    private String toCamelCase(String input) {
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = false;
        input = input.toLowerCase();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '_') {
                nextUpper = true;
            } else {
                sb.append(nextUpper ? Character.toUpperCase(c) : c);
                nextUpper = false;
            }
        }
        return sb.toString();
    }

    /**
     * Parse limitValue from plan_features to proper entitlement
     */
    private Object parseLimitValue(String limitValue, String featureCode) {
        if (limitValue == null)
            return false;

        // Numeric/unlimited features
        if ("MAX_REQUESTS_DAY".equalsIgnoreCase(featureCode) || "SEND_REQUESTS".equalsIgnoreCase(featureCode)) {
            if ("-1".equals(limitValue))
                return -1;
            try {
                return Integer.parseInt(limitValue);
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        // Boolean features
        switch (limitValue.toLowerCase()) {
            case "-1":
            case "allowed":
            case "true":
            case "1":
            case "advanced":
            case "optional":
                return true;
            case "limited":
            case "0":
            case "false":
                return false;
            default:
                return false;
        }
    }

    public ResultResponse getAllUserSubscriptionByUserId(Long userId) {
        ResultResponse response = new ResultResponse();
        try {
            if (userId == null) {
                response.setCode(400);
                response.setMessage("User ID cannot be null");
                response.setStatus(ResponseStatus.FAILURE);
                return response;
            }

            List<UserSubscriptions> userSubscriptions = userSubscriptionsRepository.findByUserId(userId);
            if (userSubscriptions != null && !userSubscriptions.isEmpty()) {
                response.setCode(200);
                response.setData(userSubscriptions);
                response.setMessage("User subscriptions retrieved successfully");
                response.setStatus(ResponseStatus.SUCCESS);
            } else {
                response.setCode(404);
                response.setData(Collections.emptyList());
                response.setMessage("No subscriptions found for this user");
                response.setStatus(ResponseStatus.FAILURE);
            }
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Failed to get user subscriptions: " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

}
