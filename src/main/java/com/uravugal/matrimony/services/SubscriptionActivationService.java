package com.uravugal.matrimony.services;

import com.uravugal.matrimony.enums.ActiveStatus;
import com.uravugal.matrimony.enums.SubscriptionStatus;
import com.uravugal.matrimony.models.Notification;
import com.uravugal.matrimony.models.SubscriptionPlan;
import com.uravugal.matrimony.models.UserEntity;
import com.uravugal.matrimony.models.UserSubscriptions;
import com.uravugal.matrimony.repositories.NotificationRepository;
import com.uravugal.matrimony.repositories.UserSubscriptionsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * The ONE place a paid plan is switched on for a member.
 *
 * Until 2026-09-15 this block lived twice inside AdminService (approvePayment for the
 * manual UTR flow, assignPlanToUser for the admin grant). The Cashfree webhook needed a
 * third caller, so it was extracted rather than pasted a third time. All three paths now
 * produce the identical user_subscriptions row and the identical member notifications;
 * they differ only in the paymentReference they record.
 *
 * Not @Transactional on purpose — matches the pre-existing admin behaviour where each
 * save commits on its own, and the push/email/in-app steps are best-effort and must never
 * roll back the subscription row.
 */
@Service
public class SubscriptionActivationService {

    @Autowired
    private UserSubscriptionsRepository userSubscriptionsRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PushNotificationService pushNotificationService;

    @Autowired
    private EmailService emailService;

    /**
     * Creates the ACTIVE subscription row (start today, end today + durationDays, autoRenew N)
     * and notifies the member by push (Direct — never plan-gated), in-app row and email.
     *
     * @param paymentReference  e.g. PAY_REQ_12, ADMIN_ASSIGN_BY_3, CF_1234567 — free text, audit only
     * @param notificationCategory  the in-app Notification.notificationCategory to write
     *                              (PAYMENT_APPROVED / PLAN_ASSIGNED / PAYMENT_SUCCESS)
     */
    /**
     * How long the plan runs, in words for the member.
     *
     * Platinum is sold as "until marriage" (durationDays is the 9999 sentinel — the same
     * convention PremiumTab and the admin plan picker use), so the mail must not print the
     * literal date that produces, which is some day in 2054. Everyone else gets a real date.
     */
    static String validityText(SubscriptionPlan plan, UserSubscriptions sub) {
        Integer days = plan.getDurationDays();
        if (days != null && days >= 9999) return "until marriage";
        if (sub.getEndDate() == null) return null;
        return "until " + sub.getEndDate().format(
                java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ENGLISH));
    }

    public UserSubscriptions activatePlan(UserEntity user, SubscriptionPlan plan,
                                          String paymentReference, String notificationCategory) {
        // 🔴 Close whatever the member was on BEFORE opening the new row. Every purchase used
        // to just insert, leaving the old row ACTIVE — the test account accumulated four live
        // plans at once. That is not only untidy: findTopByUserIdOrderByCreatedAtDesc resolves
        // entitlements from the newest ACTIVE, unexpired row, so the day the new plan lapsed
        // the member silently dropped onto an older plan they had stopped paying for.
        //
        // EXPIRED, not a new SUPERSEDED state, is deliberate — see the note below.
        // 🔴 Boost credits live on the subscription ROW (user_subscriptions.boost_credits),
        // and a boost can only be started from the ACTIVE row. Closing the old row without
        // moving its credits therefore threw away paid boosts: user 933 bought a ₹149 boost
        // at 12:33 on 2026-09-27, was moved to another plan at 12:37, and the credit was left
        // on the EXPIRED row — unusable, and reported as "remainingCredits: 0". Carry them.
        int carriedCredits = 0;
        try {
            List<UserSubscriptions> open = userSubscriptionsRepository
                    .findByUserIdAndStatus(user.getUserId(), SubscriptionStatus.ACTIVE);
            for (UserSubscriptions old : open) {
                carriedCredits += old.getBoostCredits() != null ? old.getBoostCredits() : 0;
                old.setBoostCredits(0);              // moved, not duplicated
                old.setStatus(SubscriptionStatus.EXPIRED);
                old.setEndDate(LocalDate.now());
                userSubscriptionsRepository.save(old);
            }
            if (!open.isEmpty()) {
                System.out.println("Superseded " + open.size() + " active subscription row(s) for user "
                        + user.getUserId() + " on activating " + plan.getTitle());
            }
        } catch (Exception e) {
            // Never let housekeeping block a paid activation — the member gets their plan
            // either way, and the stale row is visible to admin.
            System.out.println("Warning: could not close previous subscriptions for user "
                    + user.getUserId() + ": " + e.getMessage());
        }

        UserSubscriptions subscription = new UserSubscriptions();
        subscription.setUserId(user.getUserId());
        subscription.setSubscriptionPlanId(plan.getId());
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setAutoRenew(ActiveStatus.N);
        subscription.setStartDate(LocalDate.now());
        subscription.setEndDate(LocalDate.now().plusDays(plan.getDurationDays()));
        subscription.setPaymentReference(paymentReference);
        subscription.setBoostCredits(carriedCredits);
        subscription = userSubscriptionsRepository.save(subscription);

        // Tell the member's open app FIRST, before the slower push/email steps below.
        notifyPlanChanged(user.getUserId(), plan.getTitle());

        String body = "Your " + plan.getTitle() + " plan is now active. Enjoy your premium benefits!";

        try {
            pushNotificationService.sendPushNotificationToUserDirect(
                    user.getUserId(), "Membership Activated! 🎉", body);
        } catch (Exception e) {
            System.out.println("Warning: activation push failed: " + e.getMessage());
        }

        try {
            Notification notif = new Notification();
            notif.setReceiverId(user.getUserId());
            notif.setTitle("Membership Activated");
            notif.setMessage(body);
            notif.setNotificationCategory(notificationCategory);
            notif.setIsRead(ActiveStatus.N);
            notificationRepository.save(notif);
        } catch (Exception ignored) { }

        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            try {
                emailService.sendPlanAssignedEmail(user.getEmail(), fullName(user), plan.getTitle(),
                        validityText(plan, subscription));
            } catch (Exception e) {
                System.out.println("Warning: activation email failed: " + e.getMessage());
            }
        }

        return subscription;
    }

    /**
     * Add-on purchase (BOOST_PURCHASE): +1 boost credit on the member's active subscription.
     * Returns false when there is no active subscription to credit — the caller decides how
     * loudly to complain (admin flow used to silently skip; the gateway flow logs it).
     */
    public boolean addBoostCredit(Long userId) {
        List<UserSubscriptions> activeSubs = userSubscriptionsRepository
                .findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE);
        boolean credited = false;
        if (!activeSubs.isEmpty()) {
            UserSubscriptions sub = activeSubs.get(0);
            int current = sub.getBoostCredits() != null ? sub.getBoostCredits() : 0;
            sub.setBoostCredits(current + 1);
            userSubscriptionsRepository.save(sub);
            credited = true;
        }
        try {
            pushNotificationService.sendPushNotificationToUser(
                    userId, "Boost Ready! 🚀",
                    "Your boost credit has been added. Go to your profile and boost now!");
        } catch (Exception ignored) { }
        return credited;
    }

    /**
     * Live signal to an OPEN app that this member's plan just changed (2026-09-20).
     *
     * It is a signal, not data: the app answers by re-fetching its active subscription from
     * the REST endpoint, which stays the only source of truth for entitlements (same rule as
     * chat sync). That is why a lost event is harmless — the app also refreshes on foreground,
     * socket reconnect and login — and why planTitle here is informational only.
     *
     * Called by every path that changes a plan: activatePlan (admin approve, admin assign,
     * Cashfree webhook), the nightly expiry, and the direct-create endpoint. Best-effort by
     * contract: it must never throw into, or roll back, the subscription write it follows.
     */
    public static void notifyPlanChanged(Long userId, String planTitle) {
        if (userId == null) return;
        // ⚠️ Commit-aware on purpose. approvePayment and the nightly expiry are @Transactional,
        // and activatePlan spends seconds on push + email AFTER its save — so a signal sent
        // inline reaches the app long before the row commits, the app re-reads the OLD plan,
        // and nothing ever prompts it to look again. Inside a transaction the send is deferred
        // to afterCommit; outside one (admin assign, Cashfree) the save already committed.
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            sendPlanChanged(userId, planTitle);
                        }
                    });
        } else {
            sendPlanChanged(userId, planTitle);
        }
    }

    private static void sendPlanChanged(Long userId, String planTitle) {
        try {
            String safeTitle = planTitle == null ? "" : planTitle.replace("\\", "").replace("\"", "");
            com.uravugal.matrimony.config.UserWebSocketHandler.sendMessageToUser(userId, String.format(
                    "{\"type\":\"plan_changed\",\"data\":{\"userId\":\"%d\",\"planTitle\":\"%s\"}}",
                    userId, safeTitle));
        } catch (Exception e) {
            System.out.println("Warning: plan_changed signal failed: " + e.getMessage());
        }
    }

    /** First AND last name, falling back so a greeting never renders "Dear ,". */
    public static String fullName(UserEntity u) {
        String first = u.getFirstName() != null ? u.getFirstName().trim() : "";
        String last = u.getLastName() != null ? u.getLastName().trim() : "";
        String full = (first + " " + last).trim();
        return full.isEmpty() ? "there" : full;
    }
}
