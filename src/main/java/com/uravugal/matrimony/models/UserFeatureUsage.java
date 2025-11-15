package com.uravugal.matrimony.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "user_feature_usage")
public class UserFeatureUsage extends GenericEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "userId", nullable = false)
    private Long userId;
    
    @Column(name = "featureId", nullable = false)
    private Long featureId;
    
    @Column(name = "used_count")
    private Integer usedCount = 0;
    
    @Column(name = "last_reset_date")
    private LocalDate lastResetDate = LocalDate.now();

    @Column(name = "subscriptionId", nullable = false)
    private Long subscriptionId; // User Subscription Auto Generated ID

}
