package com.uravugal.matrimony.services;

import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ActiveStatus;
import com.uravugal.matrimony.enums.Gender;
import com.uravugal.matrimony.enums.SubscriptionStatus;
import com.uravugal.matrimony.models.Features;
import com.uravugal.matrimony.models.Notification;
import com.uravugal.matrimony.models.PlanFeatures;
import com.uravugal.matrimony.models.UserEntity;
import com.uravugal.matrimony.models.UserSubscriptions;
import com.uravugal.matrimony.repositories.FeaturesRepository;
import com.uravugal.matrimony.repositories.NotificationRepository;
import com.uravugal.matrimony.repositories.PlanFeaturesRepository;
import com.uravugal.matrimony.repositories.UserRepository;
import com.uravugal.matrimony.repositories.UserSubscriptionsRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Daily Match Alerts (F3) — Round 2 feature for Classic+ subscribers.
 *
 * Every morning at 8 AM IST, picks 3–5 fresh profiles for each Classic+ user
 * (based on opposite gender + same caste + active) and sends:
 *   1. an FCM/Expo push notification via PushNotificationService
 *   2. a row in the in-app Notification feed (category = DAILY_MATCH)
 *
 * PushNotificationService already gates on the NOTIFICATION_ALERT feature
 * (Classic+), so Free/Starter users are silently no-op'd at the push layer
 * even if they somehow get past the eligible-plan check here.
 *
 * The {@link #runNow()} method can also be called manually from
 * {@code POST /admin/runDailyMatchNow} for QA without waiting for 8 AM.
 */
@Service
public class DailyMatchScheduler {

    @Autowired
    private UserSubscriptionsRepository userSubscriptionsRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FeaturesRepository featuresRepository;

    @Autowired
    private PlanFeaturesRepository planFeaturesRepository;

    @Autowired
    private PushNotificationService pushNotificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private com.uravugal.matrimony.repositories.UserDetailRepository userDetailRepository;

    /** Plan IDs eligible for Daily Match Alerts: Classic(3), Silver(4), Gold(5), Platinum(6). */
    private static final List<Long> ELIGIBLE_PLAN_IDS = Arrays.asList(3L, 4L, 5L, 6L);

    /** 8:00 AM IST every day. */
    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Kolkata")
    @Transactional
    public void sendDailyMatches() {
        runNow();
    }

    /**
     * Manual entry point — also called by the scheduled job.
     * Returns a summary map suitable for echoing back from the admin endpoint.
     */
    public Map<String, Object> runNow() {
        int eligible = 0;
        int sent = 0;
        int skipped = 0;
        int errors = 0;

        Features feature = featuresRepository.findByCode("DAILY_MATCH_ALERT");
        if (feature == null) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", "DAILY_MATCH_ALERT feature not seeded in DB");
            err.put("ranAt", LocalDateTime.now().toString());
            return err;
        }

        List<UserSubscriptions> activeSubs =
                userSubscriptionsRepository.findByStatus(SubscriptionStatus.ACTIVE);

        for (UserSubscriptions sub : activeSubs) {
            try {
                if (!ELIGIBLE_PLAN_IDS.contains(sub.getSubscriptionPlanId())) {
                    continue;
                }

                PlanFeatures pf = planFeaturesRepository.findByFeatureIdAndSubscriptionPlanId(
                        feature.getId(), sub.getSubscriptionPlanId());
                if (pf == null) {
                    continue;
                }

                eligible++;

                Optional<UserEntity> userOpt = userRepository.findById(sub.getUserId());
                if (!userOpt.isPresent()) {
                    skipped++;
                    continue;
                }
                UserEntity user = userOpt.get();
                if (user.getCasteId() == null || user.getGender() == null) {
                    skipped++;
                    continue;
                }

                // Pick matches: opposite gender, same caste, active.
                // Mirrors the home tab's getDailyShuffledUsersByCaste seed pattern
                // so the notification body roughly matches what the user sees on home.
                Gender oppositeGender = (user.getGender() == Gender.M) ? Gender.F : Gender.M;
                List<UserEntity> matches = userRepository
                        .findAllByCasteIdAndGenderAndIsActive(
                                user.getCasteId(), oppositeGender, ActiveStatus.Y);

                if (matches == null || matches.isEmpty()) {
                    skipped++;
                    continue;
                }

                // Per-user shuffle seed (date + userId) — distinct picks for each user
                long seed = LocalDate.now().toEpochDay() + user.getUserId();
                Collections.shuffle(matches, new Random(seed));
                int take = Math.min(matches.size(), 5);
                List<UserEntity> picks = matches.subList(0, take);

                String topName = (picks.get(0).getFirstName() != null
                        && !picks.get(0).getFirstName().trim().isEmpty())
                        ? picks.get(0).getFirstName().trim()
                        : "Someone special";

                // Compute shared interests for a more personal push notification
                String title = "Good morning! \u2600\uFE0F";
                String body;
                try {
                    com.uravugal.matrimony.models.UserDetailEntity viewerDetail =
                            userDetailRepository.findByUserId(user.getUserId());
                    com.uravugal.matrimony.models.UserDetailEntity pickDetail =
                            userDetailRepository.findByUserId(picks.get(0).getUserId());
                    List<String> viewerHobbies = parseHobbiesJson(
                            viewerDetail != null ? viewerDetail.getHobbies() : null);
                    List<String> pickHobbies = parseHobbiesJson(
                            pickDetail != null ? pickDetail.getHobbies() : null);
                    List<String> common = new java.util.ArrayList<>(viewerHobbies);
                    common.retainAll(pickHobbies);

                    if (!common.isEmpty()) {
                        String interestList = common.stream().limit(3)
                                .collect(java.util.stream.Collectors.joining(", "));
                        body = topName + " shares " + common.size()
                                + " interests with you \u2014 " + interestList + " \uD83C\uDFAF";
                    } else {
                        body = (take == 1)
                                ? topName + " might be the one \u2014 view their profile now"
                                : "We found " + take + " new matches for you today \u2014 "
                                  + topName + " and more";
                    }
                } catch (Exception hobbyErr) {
                    body = (take == 1)
                            ? topName + " might be the one \u2014 view their profile now"
                            : "We found " + take + " new matches for you today \u2014 "
                              + topName + " and more";
                }

                // 1. Push (already gated on NOTIFICATION_ALERT inside PushNotificationService)
                ResultResponse pushResp = pushNotificationService
                        .sendPushNotificationToUser(user.getUserId(), title, body);

                // 2. In-app notification feed row (so missed pushes still show up)
                try {
                    Notification n = new Notification();
                    n.setSenderId(0L); // 0 = system
                    n.setReceiverId(user.getUserId());
                    n.setTitle(title);
                    n.setMessage(body);
                    n.setNotificationCategory("DAILY_MATCH");
                    n.setIsRead(ActiveStatus.N);
                    n.setCreatedAt(LocalDateTime.now());
                    n.setCreatedBy("system");
                    n.setIsActive(ActiveStatus.Y);
                    notificationRepository.save(n);
                } catch (Exception feedErr) {
                    System.out.println("DailyMatchScheduler: failed to insert in-app notification for user "
                            + user.getUserId() + " — " + feedErr.getMessage());
                }

                if (pushResp != null && pushResp.getCode() == 200) {
                    sent++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                errors++;
                System.out.println("DailyMatchScheduler error for sub " + sub.getId()
                        + ": " + e.getMessage());
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("eligible", eligible);
        summary.put("sent", sent);
        summary.put("skipped", skipped);
        summary.put("errors", errors);
        summary.put("ranAt", LocalDateTime.now().toString());
        System.out.println("\uD83D\uDCEC DailyMatchScheduler: " + summary);
        return summary;
    }

    private List<String> parseHobbiesJson(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}