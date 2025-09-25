package com.uravugal.matrimony.models;


import com.uravugal.matrimony.enums.ConnectionStatus;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "user_connections")
public class UserConnectionEntity extends GenericEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "followerId")
    private Long followerId;

    @Column(name = "followingId")
    private Long followingId;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private ConnectionStatus status;
}
