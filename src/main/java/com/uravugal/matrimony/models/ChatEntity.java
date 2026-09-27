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

    // WhatsApp-style block: a message sent by someone the recipient has BLOCKED is stored for
    // the sender (single grey tick, looks sent) but is never shown to, counted for, or marked
    // read by the recipient. NULL on legacy rows = visible.
    @Column(name = "hidden_from_receiver")
    private Boolean hiddenFromReceiver = false;

    // WhatsApp tick model: single grey = saved (isDelivered false), double grey = reached the
    // recipient's device (isDelivered), double blue = isRead. Set at send time when the recipient
    // has a live WebSocket, otherwise the moment they next connect (ChatService.onUserConnected).
    // NULL on legacy rows = not delivered; the app treats isRead as implying delivered.
    @Column(name = "isDelivered")
    private Boolean isDelivered = false;

}
