package com.uravugal.matrimony.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.uravugal.matrimony.models.PlanFeatures;

@Repository

public interface PlanFeaturesRepository extends JpaRepository<PlanFeatures, Long> {
    
    @Query("SELECT pf FROM PlanFeatures pf WHERE pf.planId = :planId")
    List<PlanFeatures> findByPlanId(@Param("planId") Long planId);
    
    @Query("SELECT pf FROM PlanFeatures pf WHERE pf.planId = :planId")
    List<PlanFeatures> findAllByPlanId(@Param("planId") Long planId);
    
    default PlanFeatures findFirstByPlanId(Long planId) {
        List<PlanFeatures> features = findByPlanId(planId);
        return features.isEmpty() ? null : features.get(0);
    }

    PlanFeatures findByFeatureIdAndPlanId(Long featureId, Long subscriptionId);
}
