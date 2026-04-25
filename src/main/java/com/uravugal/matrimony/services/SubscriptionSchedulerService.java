package com.uravugal.matrimony.services;

import com.uravugal.matrimony.enums.PaymentRequestStatus;
import com.uravugal.matrimony.enums.SubscriptionStatus;
import com.uravugal.matrimony.models.PaymentRequestEntity;
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

    @Autowired
    private ProfileBoostRepository profileBoostRepository;

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