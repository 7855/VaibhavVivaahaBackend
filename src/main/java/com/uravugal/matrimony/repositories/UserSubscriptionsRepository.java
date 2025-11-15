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

    UserSubscriptions findTopByUserIdOrderByCreatedAtDesc(Long userId);
}
