package com.uravugal.matrimony.models;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;


import com.uravugal.matrimony.enums.ApprovalStatus;
import com.uravugal.matrimony.enums.Gender;
import com.uravugal.matrimony.enums.IsUser;

@Data
@Entity
@Table(name = "users")
public class UserEntity extends GenericEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "userId")
    private Long userId;

    @Column(name = "memberId", unique = true)
    private String memberId; // e.g., "UAD000372"

    @Column(name = "firstName")
    private String firstName;

    @Column(name = "lastName")
    private String lastName;

    @Column(name = "email")
    private String email;

    @Column(name = "mobile")
    private String mobile;

    @Column(name = "password")
    private String password;

    @Column(name = "pin")
    private String pin;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender")
    private Gender gender;

    @Column(name = "dob")
    private LocalDate dob;

    @Column(name = "age")
    private String age;

    @Column(name = "casteId")
    private Integer casteId;

    @Column(name = "location")
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "userStatus")
    private ApprovalStatus userStatus;

    @Column(name = "isBlocked")
    private char isBlocked;

    @Column(name = "profileImage")
    private String profileImage;

    @Column(name = "thumbnailImage")
    private String thumbnailImage;

    @Enumerated(EnumType.STRING)
    @Column(name = "isUser")
    private IsUser isUser;

    @Column(name = "otp")
    private Integer otp;

    @Column(name = "view_count")
    private Integer viewCount = 0;
    
    @Column(name = "lastSeen")
    private LocalDateTime lastSeen;
    
    
    @Column(name = "isOnline")
    private boolean isOnline;
    
    @OneToMany
    @JoinColumn(name = "userId", referencedColumnName = "userId")
    List<UserDetailEntity> userDetail;

    
    @PrePersist protected void prePersist() {
        lastSeen = LocalDateTime.now();
    }
} 
