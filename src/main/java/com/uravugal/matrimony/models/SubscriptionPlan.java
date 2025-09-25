// src/main/java/com/uravugal/matrimony/entities/SubscriptionPlan.java
package com.uravugal.matrimony.models;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Entity
@Table(name = "subscription_plans")
public class SubscriptionPlan extends GenericEntity{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String title;
    
    @Column(nullable = false)
    private String period;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;
    
    @Column(name = "original_price", precision = 10, scale = 2)
    private BigDecimal originalPrice;
    
    @Column(length = 20)
    private String discount;
    
    @Column(length = 50)
    private String savings;
    
    @Column(name = "duration_days", nullable = false)
    private Integer durationDays;
    
    @Column(name = "is_popular", columnDefinition = "BOOLEAN DEFAULT false")
    private Boolean isPopular = false;
}