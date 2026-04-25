package com.uravugal.matrimony.models;

import com.uravugal.matrimony.enums.ActiveStatus;
import com.uravugal.matrimony.enums.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;

@Data
@Entity
@Table(name = "user_subscriptions")
public class UserSubscriptions extends GenericEntity{
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "userId", nullable = false)
    private Long userId;
    
    @Column(name = "subscriptionPlanId", nullable = false)
    private Long subscriptionPlanId;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status = SubscriptionStatus.PENDING;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "autoRenew", nullable = false)
    private ActiveStatus autoRenew = ActiveStatus.N;
    
    
    @Column(name = "startDate")
    private LocalDate startDate;
    
    @Column(name = "endDate")
    private LocalDate endDate;
    
    @Column(name = "paymentReference")
    private String paymentReference;

    /** Monthly boost credits — Gold=2, Platinum=5. Reset on 1st of each month by scheduler. */
    @Column(name = "boost_credits")
    private Integer boostCredits = 0;
}
