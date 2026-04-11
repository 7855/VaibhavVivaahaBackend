package com.uravugal.matrimony.services;

import com.uravugal.matrimony.enums.PaymentRequestStatus;
import com.uravugal.matrimony.enums.SubscriptionStatus;
import com.uravugal.matrimony.models.PaymentRequestEntity;
import com.uravugal.matrimony.models.UserSubscriptions;
import com.uravugal.matrimony.repositories.PaymentRequestRepository;
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
}