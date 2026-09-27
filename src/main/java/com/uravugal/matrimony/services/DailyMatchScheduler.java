package com.uravugal.matrimony.services;

import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ActiveStatus;
import com.uravugal.matrimony.enums.ApprovalStatus;
import com.uravugal.matrimony.enums.Gender;
import com.uravugal.matrimony.enums.IsUser;
import com.uravugal.matrimony.enums.SubscriptionStatus;
import com.uravugal.matrimony.models.DailyMatchLog;
import com.uravugal.matrimony.models.Features;
import com.uravugal.matrimony.models.Notification;
import com.uravugal.matrimony.models.PlanFeatures;
import com.uravugal.matrimony.models.UserEntity;
import com.uravugal.matrimony.models.UserSubscriptions;
import com.uravugal.matrimony.repositories.BlockedUserRepository;
import com.uravugal.matrimony.repositories.DailyMatchLogRepository;
import com.uravugal.matrimony.repositories.FeaturesRepository;
import com.uravugal.matrimony.repositories.InterestRequestRepository;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

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

    @Autowired
    private DailyMatchLogRepository dailyMatchLogRepository;

    @Autowired
    private BlockedUserRepository blockedUserRepository;

    @Autowired
    private InterestRequestRepository interestRequestRepository;

    /** Profiles per morning. */
    private static final int TAKE = 5;

    /** daily_match_log rows older than this are trimmed at the start of each run. */
    private static final int KEEP_DAYS = 180;

    /** Plan IDs eligible for Daily Match Alerts: Classic(3), Silver(4), Gold(5), Platinum(6). */
    private static final List<Long> ELIGIBLE_PLAN_IDS = Arrays.asList(3L, 4L, 5L, 6L);

    /** 8:00 AM IST every day. */
    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Kolkata")
    @Transactional
    public void sendDailyMatches() {
        runNow();
    }

    /** Scheduled path: a member who already got today's alert is left alone. */
    public Map<String, Object> runNow() {
        return runNow(false);
    }

    /**
     * Manual entry point — also called by the scheduled job.
     * Returns a summary map suitable for echoing back from the admin endpoint.
     *
     * @param force send even to members already alerted today (the admin QA button). Picks
     *              are still logged, so a forced run advances the rotation like any other.
     */
    @Transactional
    public Map<String, Object> runNow(boolean force) {
        int eligible = 0;
        int sent = 0;
        int skipped = 0;
        int errors = 0;
        int recycled = 0;          // members whose picks included someone suggested before
        int alreadySentToday = 0;

        // ⚠ Plain LocalDate.now(), not now(IST): this JVM's clock holds IST wall time under a
        // UTC default zone, so adding the zone offset would double-shift (see CLAUDE.md §17).
        LocalDate today = LocalDate.now();
        try {
            dailyMatchLogRepository.deleteOlderThan(today.minusDays(KEEP_DAYS));
        } catch (Exception trimErr) {
            System.out.println("DailyMatchScheduler: log trim failed — " + trimErr.getMessage());
        }

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

                // The cron fires once a day, but the admin QA button can fire any time — and
                // before this guard a second run on the same day sent a second push.
                if (!force && dailyMatchLogRepository.existsByReceiverIdAndSentOn(user.getUserId(), today)) {
                    alreadySentToday++;
                    continue;
                }

                // Pool: opposite gender, same caste, active — the same set the home tab shows.
                Gender oppositeGender = (user.getGender() == Gender.M) ? Gender.F : Gender.M;
                List<UserEntity> matches = userRepository
                        .findAllByCasteIdAndGenderAndIsActiveAndIsUserNot(
                                user.getCasteId(), oppositeGender, ActiveStatus.Y, IsUser.ADM);
                if (matches == null || matches.isEmpty()) {
                    skipped++;
                    continue;
                }

                // Not a "new match" for this member: blocked either way, any interest row either
                // way (connected, pending OR declined), and profiles the admins have not approved
                // — isActive defaults to Y on registration, so PENDING members are in the pool
                // and a push naming one would advertise an unvetted profile.
                Set<Long> blocked = new HashSet<>(blockedUserRepository.findBlockAdjacentUserIds(user.getUserId()));
                Set<Long> partners = new HashSet<>(interestRequestRepository.findPartnerIds(user.getUserId()));
                List<UserEntity> pool = matches.stream()
                        .filter(m -> !m.getUserId().equals(user.getUserId()))
                        .filter(m -> m.getUserStatus() == ApprovalStatus.APPROVED)
                        .filter(m -> !blocked.contains(m.getUserId()) && !partners.contains(m.getUserId()))
                        .collect(Collectors.toList());
                if (pool.isEmpty()) {
                    skipped++;
                    continue;
                }

                // Rotation. Never-suggested profiles first, then the least recently suggested,
                // ties broken by a per-member daily shuffle. Nobody repeats until everyone in the
                // pool has had a turn — and with a pool smaller than TAKE (716/718 have 3) the
                // repeats are the pool's size, not a bug, and the copy below says so.
                Map<Long, LocalDate> lastSeen = new HashMap<>();
                for (Object[] row : dailyMatchLogRepository.findLastSuggestedByReceiver(user.getUserId())) {
                    lastSeen.put(((Number) row[0]).longValue(), asLocalDate(row[1]));
                }
                Collections.shuffle(pool, new Random(today.toEpochDay() + user.getUserId()));
                pool.sort(Comparator.comparing(
                        (UserEntity m) -> lastSeen.getOrDefault(m.getUserId(), LocalDate.MIN)));
                int take = Math.min(pool.size(), TAKE);
                List<UserEntity> picks = new ArrayList<>(pool.subList(0, take));
                boolean allFresh = picks.stream().noneMatch(m -> lastSeen.containsKey(m.getUserId()));
                if (!allFresh) recycled++;

                // The headline is the one name the member actually reads. Even when every pick
                // is a repeat, rotate WHICH one is named so the push does not read identically.
                Map<Long, LocalDate> lastNamed = new HashMap<>();
                for (Object[] row : dailyMatchLogRepository.findLastHeadlinedByReceiver(user.getUserId())) {
                    lastNamed.put(((Number) row[0]).longValue(), asLocalDate(row[1]));
                }
                picks.sort(Comparator.comparing(
                        (UserEntity m) -> lastNamed.getOrDefault(m.getUserId(), LocalDate.MIN)));

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
                        body = matchBody(topName, take, allFresh);
                    }
                } catch (Exception hobbyErr) {
                    body = matchBody(topName, take, allFresh);
                }

                // 1. Push (already gated on NOTIFICATION_ALERT inside PushNotificationService)
                ResultResponse pushResp = pushNotificationService
                        .sendPushNotificationToUser(user.getUserId(), title, body);

                // Remember the picks whatever the push layer did: the in-app row below is
                // written regardless, so the member has been shown these names either way.
                try {
                    List<DailyMatchLog> rows = new ArrayList<>(picks.size());
                    for (int i = 0; i < picks.size(); i++) {
                        DailyMatchLog l = new DailyMatchLog();
                        l.setReceiverId(user.getUserId());
                        l.setSuggestedUserId(picks.get(i).getUserId());
                        l.setSentOn(today);
                        l.setHeadline(i == 0);
                        rows.add(l);
                    }
                    dailyMatchLogRepository.saveAll(rows);
                } catch (Exception logErr) {
                    System.out.println("DailyMatchScheduler: failed to log picks for user "
                            + user.getUserId() + " \u2014 " + logErr.getMessage());
                }

                // 2. In-app notification feed row (so missed pushes still show up)
                try {
                    Notification n = new Notification();
                    n.setSenderId(0L); // 0 = system
                    n.setReceiverId(user.getUserId());
                    n.setTitle(title);
                    n.setMessage(body);
                    n.setNotificationCategory("DAILY_MATCH");
                    n.setIsRead(ActiveStatus.N);
                    n.setCreatedAt(new java.util.Date());
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
        summary.put("recycled", recycled);
        summary.put("alreadySentToday", alreadySentToday);
        summary.put("ranAt", LocalDateTime.now().toString());
        System.out.println("\uD83D\uDCEC DailyMatchScheduler: " + summary);
        return summary;
    }

    /**
     * "New" only when none of the picks has been suggested to this member before. A member
     * whose caste pool is smaller than TAKE gets the same people again by arithmetic, and
     * telling them they are new every morning is what made the repeats feel like a bug.
     */
    /**
     * MAX(sentOn) comes back as java.time.LocalDate on Hibernate 6, but a java.sql.Date on
     * older mappings. A bare cast that failed would be swallowed by the per-member catch and
     * count as an error for EVERY member — nobody alerted, and nothing louder than a log line.
     */
    private static LocalDate asLocalDate(Object v) {
        if (v instanceof LocalDate d) return d;
        if (v instanceof java.sql.Date d) return d.toLocalDate();
        if (v instanceof java.util.Date d) return d.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        return LocalDate.MIN;
    }

    private static String matchBody(String topName, int take, boolean allFresh) {
        if (take == 1) {
            return allFresh
                    ? topName + " might be the one \u2014 view their profile now"
                    : topName + " is still waiting to hear from you \u2014 take another look";
        }
        return allFresh
                ? "We found " + take + " new matches for you today \u2014 " + topName + " and more"
                : "Today's picks for you \u2014 " + topName + " and " + (take - 1) + " others worth another look";
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