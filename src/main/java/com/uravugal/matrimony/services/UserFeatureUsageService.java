package com.uravugal.matrimony.services;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.PlanFeatures;
import com.uravugal.matrimony.models.UserFeatureUsage;
import com.uravugal.matrimony.models.UserSubscriptions;
import com.uravugal.matrimony.models.Features;
import com.uravugal.matrimony.repositories.FeaturesRepository;
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

    @Autowired
    private FeaturesRepository featuresRepository;

    @Autowired
    private UserSubscriptionsService userSubscriptionsService;

    public static final String SEND_REQUEST_CODE = "SEND_REQUEST";

    /**
     * Snapshot of a member's SEND_REQUEST allowance, resolved the same way
     * UserFeatureUsageController.getRequestQuota resolves it (so the number the app
     * shows and the number the server enforces can never disagree).
     */
    public static class SendRequestQuota {
        public boolean unlimited;
        public int limit;
        public int used;
        public Long subscriptionId;
        public Long featureId;
        public boolean allowed() { return unlimited || used < limit; }
    }

    /**
     * PEEK ONLY — never increments. Self-heals a missing subscription row exactly like
     * getRequestQuota does, so bulk-seeded accounts cannot slip past the check.
     */
    public SendRequestQuota checkSendRequestQuota(Long userId) {
        SendRequestQuota q = new SendRequestQuota();
        UserSubscriptions sub = userSubscriptionsService.ensureActiveSubscription(userId);
        Features feature = featuresRepository.findByCode(SEND_REQUEST_CODE);
        q.subscriptionId = sub.getId();
        q.featureId = feature != null ? feature.getId() : null;
        if (feature == null) { q.limit = 0; return q; }           // misconfigured catalog → nothing allowed
        PlanFeatures pf = planFeaturesRepository.findByFeatureIdAndSubscriptionPlanId(
                feature.getId(), sub.getSubscriptionPlanId());
        if (pf == null) { q.limit = 0; return q; }                // plan has no SEND_REQUEST row → 0
        String lv = pf.getLimitValue();
        if ("unlimited".equalsIgnoreCase(lv) || "enabled".equalsIgnoreCase(lv)) { q.unlimited = true; return q; }
        try { q.limit = Integer.parseInt(lv.trim()); } catch (Exception e) { q.limit = 0; }
        q.used = userFeatureUsageRepository
                .findByUserIdAndSubscriptionIdAndFeatureId(userId, sub.getId(), feature.getId())
                .map(UserFeatureUsage::getUsedCount).orElse(0);
        return q;
    }

    /**
     * Spend ONE send-request against a quota returned by checkSendRequestQuota. Creates the
     * usage row on first use. No-op for unlimited plans. Called by InterestRequestService
     * only AFTER the interest row has been written, so a failed send never costs a request.
     */
    @Transactional
    public void chargeSendRequest(Long userId, SendRequestQuota q) {
        if (q == null || q.unlimited || q.featureId == null || q.subscriptionId == null) return;
        UserFeatureUsage usage = userFeatureUsageRepository
                .findByUserIdAndSubscriptionIdAndFeatureId(userId, q.subscriptionId, q.featureId)
                .orElseGet(() -> {
                    UserFeatureUsage u = new UserFeatureUsage();
                    u.setUserId(userId);
                    u.setSubscriptionId(q.subscriptionId);
                    u.setFeatureId(q.featureId);
                    u.setUsedCount(0);
                    return u;
                });
        usage.setUsedCount(usage.getUsedCount() + 1);
        userFeatureUsageRepository.save(usage);
    }

    public ResultResponse updateUsedCount(Long userId, Long subscriptionId, Long featureId) {
        ResultResponse response = new ResultResponse();
        try {
            // SEND_REQUEST is charged by the server inside sendInterestRequest since 2026-09-26.
            // The app (every build in the field) still calls this endpoint right before it sends,
            // so for this ONE feature it is now a check without an increment: same 200 / 401
            // answers the app already branches on, no double charge, no app change needed.
            Features sendReq = featuresRepository.findByCode(SEND_REQUEST_CODE);
            if (sendReq != null && featureId != null && featureId.equals(sendReq.getId())) {
                SendRequestQuota q = checkSendRequestQuota(userId);
                if (q.allowed()) {
                    response.setCode(200);
                    response.setMessage("Used count updated successfully");
                    response.setStatus(ResponseStatus.SUCCESS);
                } else {
                    response.setCode(401);
                    response.setMessage("Feature usage limit exceeded");
                    response.setStatus(ResponseStatus.FAILURE);
                }
                return response;
            }

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

            // Free plan check removed — Free users now have limited features (e.g. 3 requests)
            // Feature-level limits in planFeatures table handle gating per plan

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
                // First-time use — create a new usage record with usedCount=1
                int limit = Integer.parseInt(planFeatures.getLimitValue());
                if (limit > 0) {
                    UserFeatureUsage newUsage = new UserFeatureUsage();
                    newUsage.setUserId(userId);
                    newUsage.setSubscriptionId(subscriptionId);
                    newUsage.setFeatureId(featureId);
                    newUsage.setUsedCount(1);
                    userFeatureUsageRepository.save(newUsage);

                    response.setCode(200);
                    response.setMessage("Feature usage started (1/" + limit + ")");
                    response.setStatus(ResponseStatus.SUCCESS);
                } else {
                    response.setCode(401);
                    response.setMessage("Feature usage limit exceeded");
                    response.setStatus(ResponseStatus.FAILURE);
                }
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
