package com.uravugal.matrimony.controllers;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.enums.SubscriptionStatus;
import com.uravugal.matrimony.models.Features;
import com.uravugal.matrimony.models.PlanFeatures;
import com.uravugal.matrimony.models.UserFeatureUsage;
import com.uravugal.matrimony.models.UserSubscriptions;
import com.uravugal.matrimony.repositories.FeaturesRepository;
import com.uravugal.matrimony.repositories.PlanFeaturesRepository;
import com.uravugal.matrimony.repositories.UserFeatureUsageRepository;
import com.uravugal.matrimony.repositories.UserSubscriptionsRepository;
import com.uravugal.matrimony.services.UserFeatureUsageService;

@RestController
@RequestMapping("/userFeatureUsage")
public class UserFeatureUsageController {

    @Autowired
    private UserFeatureUsageService userFeatureUsageService;

    @Autowired
    private UserSubscriptionsRepository userSubscriptionsRepository;

    @Autowired
    private FeaturesRepository featuresRepository;

    @Autowired
    private PlanFeaturesRepository planFeaturesRepository;

    @Autowired
    private UserFeatureUsageRepository userFeatureUsageRepository;

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

    @GetMapping("/requestQuota/{encodedUserId}")
    public ResultResponse getRequestQuota(@PathVariable String encodedUserId) {
        ResultResponse response = new ResultResponse();
        try {
            Long userId = Long.parseLong(new String(Base64.getDecoder().decode(encodedUserId)));

            java.util.List<UserSubscriptions> subs = userSubscriptionsRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE);
            if (subs.isEmpty()) {
                // Free user with no active subscription row — check plan 1 features
                UserSubscriptions freeSub = userSubscriptionsRepository.findTopByUserIdOrderByCreatedAtDesc(userId);
                if (freeSub != null) {
                    subs = java.util.List.of(freeSub);
                }
            }

            Features sendRequestFeature = featuresRepository.findByCode("SEND_REQUEST");
            if (sendRequestFeature == null) {
                response.setCode(404);
                response.setMessage("SEND_REQUEST feature not found");
                response.setStatus(ResponseStatus.FAILURE);
                return response;
            }

            Map<String, Object> data = new HashMap<>();

            // Determine the plan ID: from active subscription or default to Free (plan 1)
            Long planId = 1L; // Free plan default
            Long subId = null;
            if (!subs.isEmpty()) {
                UserSubscriptions sub = subs.get(0);
                planId = sub.getSubscriptionPlanId();
                subId = sub.getId();
            }

            {
                PlanFeatures pf = planFeaturesRepository.findByFeatureIdAndSubscriptionPlanId(
                        sendRequestFeature.getId(), planId);

                if (pf == null) {
                    data.put("total", 0);
                    data.put("used", 0);
                    data.put("remaining", 0);
                    data.put("unlimited", false);
                } else if ("unlimited".equalsIgnoreCase(pf.getLimitValue())) {
                    data.put("total", -1);
                    data.put("used", 0);
                    data.put("remaining", -1);
                    data.put("unlimited", true);
                } else {
                    int total = Integer.parseInt(pf.getLimitValue());
                    int used = 0;
                    if (subId != null) {
                        Optional<UserFeatureUsage> usage = userFeatureUsageRepository
                                .findByUserIdAndSubscriptionIdAndFeatureId(userId, subId, sendRequestFeature.getId());
                        used = usage.map(UserFeatureUsage::getUsedCount).orElse(0);
                    }
                    data.put("total", total);
                    data.put("used", used);
                    data.put("remaining", Math.max(0, total - used));
                    data.put("unlimited", false);
                }
            }

            response.setCode(200);
            response.setData(data);
            response.setMessage("Request quota fetched");
            response.setStatus(ResponseStatus.SUCCESS);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Error: " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    @Autowired
    private com.uravugal.matrimony.repositories.ChatRepository chatRepository;

    @GetMapping("/conversationQuota/{encodedUserId}")
    public ResultResponse getConversationQuota(@PathVariable String encodedUserId) {
        ResultResponse response = new ResultResponse();
        try {
            Long userId = Long.parseLong(new String(Base64.getDecoder().decode(encodedUserId)));

            java.util.List<UserSubscriptions> subs = userSubscriptionsRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE);
            Long planId = 1L;
            if (!subs.isEmpty()) {
                planId = subs.get(0).getSubscriptionPlanId();
            }

            Features messageFeature = featuresRepository.findByCode("MESSAGE");
            Map<String, Object> data = new HashMap<>();

            if (messageFeature == null) {
                data.put("total", 0);
                data.put("used", 0);
                data.put("remaining", 0);
                data.put("unlimited", false);
            } else {
                PlanFeatures pf = planFeaturesRepository.findByFeatureIdAndSubscriptionPlanId(
                        messageFeature.getId(), planId);

                if (pf == null) {
                    data.put("total", 0);
                    data.put("used", 0);
                    data.put("remaining", 0);
                    data.put("unlimited", false);
                } else if ("enabled".equalsIgnoreCase(pf.getLimitValue()) || "unlimited".equalsIgnoreCase(pf.getLimitValue())) {
                    data.put("total", -1);
                    data.put("used", 0);
                    data.put("remaining", -1);
                    data.put("unlimited", true);
                } else if (pf.getLimitValue().matches("\\d+")) {
                    int total = Integer.parseInt(pf.getLimitValue());
                    Long used = chatRepository.countDistinctConversationsBySenderId(userId);
                    int usedInt = used != null ? used.intValue() : 0;
                    data.put("total", total);
                    data.put("used", usedInt);
                    data.put("remaining", Math.max(0, total - usedInt));
                    data.put("unlimited", false);
                } else {
                    data.put("total", 0);
                    data.put("used", 0);
                    data.put("remaining", 0);
                    data.put("unlimited", false);
                }
            }

            response.setCode(200);
            response.setData(data);
            response.setMessage("Conversation quota fetched");
            response.setStatus(ResponseStatus.SUCCESS);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Error: " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }
}
