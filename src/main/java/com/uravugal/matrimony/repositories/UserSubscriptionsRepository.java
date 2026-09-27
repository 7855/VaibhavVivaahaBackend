package com.uravugal.matrimony.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.uravugal.matrimony.enums.SubscriptionStatus;
import com.uravugal.matrimony.models.UserSubscriptions;

@Repository
public interface UserSubscriptionsRepository extends JpaRepository<UserSubscriptions, Long> {

    @Query("SELECT us FROM UserSubscriptions us WHERE us.userId = :userId AND us.status = :status ORDER BY us.endDate DESC")
    List<UserSubscriptions> findByUserIdAndStatus(@Param("userId") Long userId, @Param("status") SubscriptionStatus status);
    
    @Query("SELECT us FROM UserSubscriptions us WHERE us.userId = :userId AND us.status = :status ORDER BY us.endDate DESC LIMIT 1")
    Optional<UserSubscriptions> findTopByUserIdAndStatusOrderByEndDateDesc(@Param("userId") Long userId, @Param("status") SubscriptionStatus status);
    Optional<UserSubscriptions> findLatestByUserIdAndStatus(@Param("userId") Long userId, @Param("status") SubscriptionStatus status);
    
    List<UserSubscriptions> findByUserId(Long userId);

    /**
     * The member's CURRENT subscription for plan-gating, or null.
     *
     * 🔴 This used to be a derived query with no status filter — "latest row regardless of
     * status" — and it is the plan gate for 14 call sites (contact reveal, voice call, subcaste
     * search, interest quotas, verifications, push gating…). An EXPIRED member therefore kept
     * passing every one of those gates until they bought something new, because the nightly
     * scheduler only flips status; the row itself stays newest.
     *
     * Now a default method: latest ACTIVE row whose endDate hasn't passed (NULL endDate =
     * lifetime, e.g. Free). endDate is belt-and-braces on top of the status check, so a member
     * is gated correctly even in the hours between expiry and the 00:00 scheduler run.
     *
     * Kept the original name and signature so no caller changed. Every audited caller intends
     * "current active plan" — the one same-named finder on PaymentRequestRepository is a
     * different repository and is untouched.
     */
    default UserSubscriptions findTopByUserIdOrderByCreatedAtDesc(Long userId) {
        java.time.LocalDate today = java.time.LocalDate.now();
        return findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE).stream()
                .filter(s -> s.getEndDate() == null || !s.getEndDate().isBefore(today))
                .max(java.util.Comparator.comparing(
                        UserSubscriptions::getCreatedAt,
                        java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())))
                .orElse(null);
    }

    List<UserSubscriptions> findByStatus(SubscriptionStatus status);
}
