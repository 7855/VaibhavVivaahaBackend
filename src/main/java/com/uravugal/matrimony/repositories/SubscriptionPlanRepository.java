package com.uravugal.matrimony.repositories;

import com.uravugal.matrimony.enums.ActiveStatus;
import com.uravugal.matrimony.models.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {
    List<SubscriptionPlan> findByIsActiveOrderByPriceAsc(ActiveStatus isActive);
    List<SubscriptionPlan> findByIsActiveAndIsPopularOrderByPriceAsc(ActiveStatus isActive, Boolean isPopular);
    
    default List<SubscriptionPlan> findByIsActiveTrueOrderByPriceAsc() {
        return findByIsActiveOrderByPriceAsc(ActiveStatus.Y);
    }
    
    default List<SubscriptionPlan> findByIsActiveTrueAndIsPopularTrueOrderByPriceAsc() {
        return findByIsActiveAndIsPopularOrderByPriceAsc(ActiveStatus.Y, true);
    }
}