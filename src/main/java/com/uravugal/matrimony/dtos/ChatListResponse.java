package com.uravugal.matrimony.dtos;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChatListResponse {
    private Long conversationId;
    private Long otherUserId;
    private String otherUserName;
    private String lastMessage;
    private LocalDateTime lastMessageTime;
    private boolean isRead;
    private String status;
    private String profileImage;
    private Integer unreadMessageCount;

    // Verification flags — shield beside other user's name in chat list rows
    private Boolean idVerified;
    private Boolean educationVerified;
    private Boolean incomeVerified;
}