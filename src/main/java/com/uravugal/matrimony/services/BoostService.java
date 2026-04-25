package com.uravugal.matrimony.services;

import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.enums.SubscriptionStatus;
import com.uravugal.matrimony.models.ProfileBoost;
import com.uravugal.matrimony.models.UserSubscriptions;
import com.uravugal.matrimony.repositories.ProfileBoostRepository;
import com.uravugal.matrimony.repositories.UserSubscriptionsRepository;

import com.uravugal.matrimony.dtos.PaginatedResultResponse;
import com.uravugal.matrimony.dtos.PaginationData;
import com.uravugal.matrimony.models.UserEntity;
import com.uravugal.matrimony.repositories.UserRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BoostService {

    /** Plan-specific monthly boost credits. */
    private static final Map<Long, Integer> PLAN_BOOST_CREDITS = Map.of(
            5L, 2,  // Gold
            6L, 5   // Platinum
    );

    @Autowired
    private ProfileBoostRepository profileBoostRepository;

    @Autowired
    private UserSubscriptionsRepository userSubscriptionsRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * Start a 24-hour profile boost.
     * source = "MONTHLY_CREDIT" (uses included credits) or "PURCHASED" (from approved payment).
     */
    @Transactional
    public ResultResponse startBoost(String encodedUserId, String source) {
        ResultResponse resp = new ResultResponse();
        try {
            Long userId = Long.parseLong(new String(Base64.getDecoder().decode(encodedUserId)));

            // Check no active boost already
            ProfileBoost existing = profileBoostRepository
                    .findFirstByUserIdAndStatusOrderByExpiresAtDesc(userId, "ACTIVE");
            if (existing != null && existing.getExpiresAt().isAfter(LocalDateTime.now())) {
                resp.setCode(409);
                resp.setStatus(ResponseStatus.FAILURE);
                resp.setMessage("BOOST_ALREADY_ACTIVE");
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("expiresAt", existing.getExpiresAt().toString());
                resp.setData(data);
                return resp;
            }

            // Mark stale "ACTIVE" boosts as expired
            if (existing != null) {
                existing.setStatus("EXPIRED");
                profileBoostRepository.save(existing);
            }

            // Get active subscription
            List<UserSubscriptions> subs = userSubscriptionsRepository
                    .findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE);
            UserSubscriptions sub = subs.isEmpty() ? null : subs.get(0);

            if ("MONTHLY_CREDIT".equals(source)) {
                if (sub == null) {
                    resp.setCode(403);
                    resp.setStatus(ResponseStatus.FAILURE);
                    resp.setMessage("PLAN_UPGRADE_REQUIRED");
                    return resp;
                }
                int credits = sub.getBoostCredits() != null ? sub.getBoostCredits() : 0;
                if (credits <= 0) {
                    resp.setCode(400);
                    resp.setStatus(ResponseStatus.FAILURE);
                    resp.setMessage("NO_BOOST_CREDITS");
                    return resp;
                }
                // Decrement credit
                sub.setBoostCredits(credits - 1);
                userSubscriptionsRepository.save(sub);
            }
            // For "PURCHASED" source — admin already added boost_credits via payment approval flow

            // Create the boost
            ProfileBoost boost = new ProfileBoost();
            boost.setUserId(userId);
            boost.setStartedAt(LocalDateTime.now());
            boost.setExpiresAt(LocalDateTime.now().plusHours(24));
            boost.setSource(source);
            boost.setStatus("ACTIVE");
            ProfileBoost saved = profileBoostRepository.save(boost);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("boostId", saved.getId());
            data.put("expiresAt", saved.getExpiresAt().toString());
            data.put("remainingCredits", sub != null ? sub.getBoostCredits() : 0);

            resp.setCode(200);
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setMessage("Profile boost activated for 24 hours!");
            resp.setData(data);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setStatus(ResponseStatus.FAILURE);
            resp.setMessage("Error: " + e.getMessage());
        }
        return resp;
    }

    /** Get current boost status + remaining credits for the profile page. */
    public ResultResponse getBoostStatus(String encodedUserId) {
        ResultResponse resp = new ResultResponse();
        try {
            Long userId = Long.parseLong(new String(Base64.getDecoder().decode(encodedUserId)));

            ProfileBoost active = profileBoostRepository
                    .findFirstByUserIdAndStatusOrderByExpiresAtDesc(userId, "ACTIVE");
            boolean isActive = active != null && active.getExpiresAt().isAfter(LocalDateTime.now());

            List<UserSubscriptions> subs = userSubscriptionsRepository
                    .findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE);
            UserSubscriptions sub = subs.isEmpty() ? null : subs.get(0);

            int remainingCredits = sub != null && sub.getBoostCredits() != null ? sub.getBoostCredits() : 0;
            Long planId = sub != null ? sub.getSubscriptionPlanId() : 1L;
            int creditsPerMonth = PLAN_BOOST_CREDITS.getOrDefault(planId, 0);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("isBoostActive", isActive);
            data.put("expiresAt", isActive ? active.getExpiresAt().toString() : null);
            data.put("remainingCredits", remainingCredits);
            data.put("creditsPerMonth", creditsPerMonth);
            data.put("canBuyAddon", planId >= 3); // Classic+ can buy add-ons

            resp.setCode(200);
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setMessage("Boost status fetched");
            resp.setData(data);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setStatus(ResponseStatus.FAILURE);
            resp.setMessage("Error: " + e.getMessage());
        }
        return resp;
    }

    // ── Admin methods ──

    /** Admin: paginated list of all boosts with user details, optionally filtered by status. */
    public PaginatedResultResponse adminList(String status, Integer page, Integer size) {
        PaginatedResultResponse resp = new PaginatedResultResponse();
        try {
            PageRequest pr = PageRequest.of(page != null ? page : 0, size != null ? size : 20);
            Page<ProfileBoost> p;
            if (status != null && !status.isBlank()) {
                p = profileBoostRepository.findByStatusOrderByIdDesc(status, pr);
            } else {
                p = profileBoostRepository.findAllByOrderByIdDesc(pr);
            }

            List<Map<String, Object>> enriched = new ArrayList<>();
            for (ProfileBoost boost : p.getContent()) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", boost.getId());
                row.put("userId", boost.getUserId());
                row.put("startedAt", boost.getStartedAt());
                row.put("expiresAt", boost.getExpiresAt());
                row.put("source", boost.getSource());
                row.put("status", boost.getStatus());
                row.put("createdAt", boost.getCreatedAt());

                // Check if still truly active
                boolean isLive = "ACTIVE".equals(boost.getStatus())
                        && boost.getExpiresAt() != null
                        && boost.getExpiresAt().isAfter(LocalDateTime.now());
                row.put("isLive", isLive);

                // Enrich with user info
                try {
                    UserEntity u = userRepository.findById(boost.getUserId()).orElse(null);
                    if (u != null) {
                        row.put("userName",
                                ((u.getFirstName() == null ? "" : u.getFirstName()) + " " +
                                 (u.getLastName() == null ? "" : u.getLastName())).trim());
                        row.put("userMobile", u.getMobile());
                        row.put("userProfileImage", u.getProfileImage());
                        row.put("memberId", u.getMemberId());
                    }
                } catch (Exception ignored) {}

                // Enrich with subscription info (credits remaining)
                try {
                    List<UserSubscriptions> subs = userSubscriptionsRepository
                            .findByUserIdAndStatus(boost.getUserId(), SubscriptionStatus.ACTIVE);
                    if (!subs.isEmpty()) {
                        UserSubscriptions sub = subs.get(0);
                        row.put("boostCredits", sub.getBoostCredits());
                        row.put("planId", sub.getSubscriptionPlanId());
                    }
                } catch (Exception ignored) {}

                enriched.add(row);
            }

            resp.setCode(200);
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setMessage("Boosts fetched");
            resp.setData(enriched);
            PaginationData pg = new PaginationData();
            pg.setTotalPages(p.getTotalPages());
            pg.setTotalElements(p.getTotalElements());
            pg.setCurrentPage(p.getNumber());
            pg.setPageSize(p.getSize());
            resp.setPaginationData(pg);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setStatus(ResponseStatus.FAILURE);
            resp.setMessage("Error: " + e.getMessage());
        }
        return resp;
    }

    /** Admin: manually grant N boost credits to a user. */
    public ResultResponse adminGrantCredits(Long userId, Integer credits) {
        ResultResponse resp = new ResultResponse();
        try {
            if (userId == null || credits == null || credits < 1) {
                resp.setCode(400);
                resp.setStatus(ResponseStatus.FAILURE);
                resp.setMessage("userId and credits (>=1) required");
                return resp;
            }
            List<UserSubscriptions> subs = userSubscriptionsRepository
                    .findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE);
            if (subs.isEmpty()) {
                resp.setCode(404);
                resp.setStatus(ResponseStatus.FAILURE);
                resp.setMessage("No active subscription found for user " + userId);
                return resp;
            }
            UserSubscriptions sub = subs.get(0);
            int current = sub.getBoostCredits() != null ? sub.getBoostCredits() : 0;
            sub.setBoostCredits(current + credits);
            userSubscriptionsRepository.save(sub);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("userId", userId);
            data.put("creditsAdded", credits);
            data.put("totalCredits", sub.getBoostCredits());

            resp.setCode(200);
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setMessage(credits + " boost credit(s) granted to user " + userId);
            resp.setData(data);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setStatus(ResponseStatus.FAILURE);
            resp.setMessage("Error: " + e.getMessage());
        }
        return resp;
    }

    /** Admin: immediately expire an active boost. */
    @Transactional
    public ResultResponse adminRevokeBoost(Long boostId) {
        ResultResponse resp = new ResultResponse();
        try {
            ProfileBoost boost = profileBoostRepository.findById(boostId).orElse(null);
            if (boost == null) {
                resp.setCode(404);
                resp.setStatus(ResponseStatus.FAILURE);
                resp.setMessage("Boost not found");
                return resp;
            }
            boost.setStatus("EXPIRED");
            boost.setExpiresAt(LocalDateTime.now());
            profileBoostRepository.save(boost);

            resp.setCode(200);
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setMessage("Boost revoked");
            resp.setData(boost);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setStatus(ResponseStatus.FAILURE);
            resp.setMessage("Error: " + e.getMessage());
        }
        return resp;
    }
}