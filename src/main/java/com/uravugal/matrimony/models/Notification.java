package com.uravugal.matrimony.models;

import com.uravugal.matrimony.enums.ActiveStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "notifications")
@Data
public class Notification extends GenericEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long notificationId;

    @Column(name = "senderId")
    private Long senderId;

    @Column(name = "receiverId")
    private Long receiverId;

    @Column(length = 32, columnDefinition = "enum('Y','N') default 'N'")
	@Enumerated(value = EnumType.STRING)
	private ActiveStatus isRead = ActiveStatus.N;


    @Column(name = "title")
    private String title;

    @Column(name = "message")
    private String message;

    @Column(name = "notificationCategory")
    private String notificationCategory;
    
  
}
