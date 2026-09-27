package com.uravugal.matrimony.services;

import com.uravugal.matrimony.enums.PaymentRequestStatus;
import com.uravugal.matrimony.enums.SubscriptionStatus;
import com.uravugal.matrimony.models.PaymentRequestEntity;
import com.uravugal.matrimony.models.PaymentOrder;
import com.uravugal.matrimony.models.ProfileBoost;
import com.uravugal.matrimony.models.UserSubscriptions;
import com.uravugal.matrimony.repositories.PaymentRequestRepository;
import com.uravugal.matrimony.repositories.ProfileBoostRepository;
import com.uravugal.matrimony.repositories.UserSubscriptionsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SubscriptionSchedulerService {

    @Autowired
    private UserSubscriptionsRepository userSubscriptionsRepository;

    @Autowired
    private PaymentRequestRepository paymentRequestRepository;

    // @Lazy: the scheduler and CashfreeService both sit in the service layer and
    // Cashfree's own wiring reaches back here, so eager injection risks a cycle at boot.
    @Autowired
    @org.springframework.context.annotation.Lazy
    private CashfreeService cashfreeService;

    @Autowired
    private ProfileBoostRepository profileBoostRepository;

    @Autowired
    private com.uravugal.matrimony.repositories.PaymentOrderRepository paymentOrderRepository;

    private static final java.util.Map<Long, Integer> PLAN_BOOST_CREDITS = java.util.Map.of(
            5L, 2,  // Gold
            6L, 5   // Platinum
    );

    /**
     * Runs daily at midnight — expires subscriptions where endDate has passed.
     */
    @Scheduled(cron = "0 0 0 * * *") // Every day at 00:00
    @Transactional
    public void expireSubscriptions() {
        List<UserSubscriptions> activeSubscriptions = userSubscriptionsRepository.findByStatus(SubscriptionStatus.ACTIVE);

        int expiredCount = 0;
        for (UserSubscriptions subscription : activeSubscriptions) {
            if (subscription.getEndDate() != null && subscription.getEndDate().isBefore(LocalDate.now())) {
                subscription.setStatus(SubscriptionStatus.EXPIRED);
                userSubscriptionsRepository.save(subscription);
                // A member with the app open at midnight drops to Free live, not on next restart.
                SubscriptionActivationService.notifyPlanChanged(subscription.getUserId(), "Free");
                expiredCount++;
            }
        }

        if (expiredCount > 0) {
            System.out.println("🔄 Subscription scheduler: expired " + expiredCount + " subscriptions.");
        }
    }

    /**
     * Runs daily at 1 AM — expires PENDING payment requests older than 7 days.
     */
    /**
     * Cashfree orders that were never paid: flip ACTIVE -> EXPIRED once their
     * order_expiry_time has passed. Local bookkeeping only (no gateway call);
     * a PAID order is never touched because markPaid already moved it off ACTIVE.
     */
    @Scheduled(cron = "0 15 0 * * *") // Every day at 00:15
    public void expirePaymentOrders() {
        try {
            // 🔴 Ask Cashfree BEFORE writing anything off. This used to expire purely on
            // the clock, which loses money in a real and silent way: the member pays, the
            // webhook does not arrive (a deploy, a network blip, Cloudflare), and they
            // close the app so nothing ever polls. At 00:15 the order was stamped EXPIRED
            // and it then looked exactly like an abandoned checkout, so nobody would ever
            // look at it again — while the money had actually been taken.
            //
            // getOrderStatus already re-checks with Cashfree and fulfils a PAID order
            // (granting the plan and sending the notifications), so reconciling is just
            // calling it once per stale order. Each one is isolated: a single failure must
            // not stop the rest of the sweep.
            int recovered = 0;
            for (PaymentOrder stale : paymentOrderRepository.findStaleActive(java.time.LocalDateTime.now())) {
                try {
                    cashfreeService.getOrderStatus(stale.getOrderId(), null);
                    PaymentOrder after = paymentOrderRepository.findByOrderId(stale.getOrderId()).orElse(stale);
                    if (PaymentOrder.STATUS_PAID.equals(after.getOrderStatus())) {
                        recovered++;
                        System.out.println("reconcile: " + stale.getOrderId() + " was PAID after all — plan granted");
                    }
                } catch (Exception e) {
                    System.out.println("reconcile failed for " + stale.getOrderId() + ": " + e.getMessage());
                }
            }
            if (recovered > 0) System.out.println("expirePaymentOrders: recovered " + recovered + " paid order(s) before expiry");

            // Whatever Cashfree did NOT confirm as paid is genuinely abandoned.
            int n = paymentOrderRepository.expireStale(java.time.LocalDateTime.now());
            if (n > 0) System.out.println("expirePaymentOrders: " + n + " Cashfree order(s) marked EXPIRED");
        } catch (Exception e) {
            System.out.println("expirePaymentOrders failed: " + e.getMessage());
        }
    }

    @Scheduled(cron = "0 0 1 * * *") // Every day at 01:00
    @Transactional
    public void expirePaymentRequests() {
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        List<PaymentRequestEntity> pendingRequests = paymentRequestRepository.findByStatus(PaymentRequestStatus.PENDING);

        int expiredCount = 0;
        for (PaymentRequestEntity request : pendingRequests) {
            if (request.getCreatedAt() != null && request.getCreatedAt().isBefore(sevenDaysAgo)) {
                request.setStatus(PaymentRequestStatus.EXPIRED);
                paymentRequestRepository.save(request);
                expiredCount++;
            }
        }

        if (expiredCount > 0) {
            System.out.println("🔄 Payment scheduler: expired " + expiredCount + " pending payment requests.");
        }
    }

    /**
     * Every 10 minutes — mark expired profile boosts as EXPIRED.
     */
    @Scheduled(cron = "0 */10 * * * *")
    @Transactional
    public void expireProfileBoosts() {
        List<ProfileBoost> staleBoosts = profileBoostRepository
                .findAllByStatusAndExpiresAtBefore("ACTIVE", LocalDateTime.now());
        int count = 0;
        for (ProfileBoost boost : staleBoosts) {
            boost.setStatus("EXPIRED");
            profileBoostRepository.save(boost);
            count++;
        }
        if (count > 0) {
            System.out.println("🚀 Boost scheduler: expired " + count + " profile boosts.");
        }
    }

    /**
     * 1st of every month at 00:05 IST — reset boost credits for Gold/Platinum subscribers.
     */
    @Scheduled(cron = "0 5 0 1 * *", zone = "Asia/Kolkata")
    @Transactional
    public void resetMonthlyBoostCredits() {
        List<UserSubscriptions> activeSubs = userSubscriptionsRepository.findByStatus(SubscriptionStatus.ACTIVE);
        int resetCount = 0;
        for (UserSubscriptions sub : activeSubs) {
            Integer monthlyCredits = PLAN_BOOST_CREDITS.get(sub.getSubscriptionPlanId());
            if (monthlyCredits != null && monthlyCredits > 0) {
                sub.setBoostCredits(monthlyCredits);
                userSubscriptionsRepository.save(sub);
                resetCount++;
            }
        }
        if (resetCount > 0) {
            System.out.println("🚀 Boost scheduler: reset credits for " + resetCount + " subscribers.");
        }
    }
}