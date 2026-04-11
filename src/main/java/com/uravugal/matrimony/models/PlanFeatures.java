package com.uravugal.matrimony.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "planFeatures")
public class PlanFeatures extends GenericEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subscriptionPlanId", nullable = false)
    private Long subscriptionPlanId; // Subscription Plan Id from subscriptionPlan table

    @Column(name = "featureId", nullable = false)
    private Long featureId; // Feature Id from features table

    @Column(name = "limit_value", columnDefinition = "VARCHAR(50) DEFAULT NULL")
    private String limitValue; // like 50 request can send User. Incrasing count after each request in
                               // userFeatureUsage table.

    @Column(name = "limit_period", columnDefinition = "VARCHAR(50) DEFAULT NULL")
    private String limitPeriod;
}
