package com.uravugal.matrimony.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.uravugal.matrimony.models.UserFeatureUsage;


public interface UserFeatureUsageRepository extends JpaRepository<UserFeatureUsage, Long> {

    Optional<UserFeatureUsage> findByUserIdAndSubscriptionIdAndFeatureId(Long userId,
            Long subscriptionId, Long featureId);

    Optional<UserFeatureUsage> findByUserIdAndFeatureId(Long userId, Long id);
    
}
