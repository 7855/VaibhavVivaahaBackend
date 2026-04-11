package com.uravugal.matrimony.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.uravugal.matrimony.models.PlanFeatures;

@Repository

public interface PlanFeaturesRepository extends JpaRepository<PlanFeatures, Long> {

    @Query("SELECT pf FROM PlanFeatures pf WHERE pf.subscriptionPlanId = :subscriptionPlanId")
    List<PlanFeatures> findBySubscriptionPlanId(@Param("subscriptionPlanId") Long subscriptionPlanId);

    @Query("SELECT pf FROM PlanFeatures pf WHERE pf.subscriptionPlanId = :subscriptionPlanId")
    List<PlanFeatures> findAllByPlanId(@Param("subscriptionPlanId") Long subscriptionPlanId);

    default PlanFeatures findFirstBySubscriptionPlanId(Long subscriptionPlanId) {
        List<PlanFeatures> features = findBySubscriptionPlanId(subscriptionPlanId);
        return features.isEmpty() ? null : features.get(0);
    }

    PlanFeatures findByFeatureIdAndSubscriptionPlanId(Long featureId, Long subscriptionPlanId);

}
