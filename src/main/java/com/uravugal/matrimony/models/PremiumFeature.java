package com.uravugal.matrimony.models;

import com.uravugal.matrimony.enums.ActiveStatus;
import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "premium_features")
public class PremiumFeature {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String icon;
    
    @Column(nullable = false)
    private String title;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "text_color", length = 20)
    private String textColor;
    
    @Column(name = "bg_color", length = 20)
    private String bgColor;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "is_active", columnDefinition = "enum('Y', 'N') default 'Y'")
    private ActiveStatus isActive = ActiveStatus.Y;
    
    @Column(name = "display_order")
    private Integer displayOrder;
    
    // Explicit getter and setter for isActive to match the expected method names
    public ActiveStatus getActive() {
        return isActive;
    }
    
    public void setActive(ActiveStatus active) {
        isActive = active;
    }
}