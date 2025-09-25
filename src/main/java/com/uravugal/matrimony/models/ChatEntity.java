package com.uravugal.matrimony.models;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "chats")
public class ChatEntity extends GenericEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Long id;

    @Column(name = "conversationId", nullable = false)
    private Long conversationId;

    @Column(name = "senderId", nullable = false)
    private Long senderId; 

    @Column(name = "message", nullable = false)
    private String message;

    @Column(name = "isRead")
    private Boolean isRead = false;

}
