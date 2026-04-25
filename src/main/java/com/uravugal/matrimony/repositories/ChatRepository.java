package com.uravugal.matrimony.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.uravugal.matrimony.enums.ActiveStatus;
import com.uravugal.matrimony.models.ChatEntity;

@Repository
public interface ChatRepository extends JpaRepository<ChatEntity, Long> {
    
    @Query("SELECT COUNT(c) FROM ChatEntity c WHERE c.conversationId = :conversationId AND c.senderId != :userId AND c.isRead = false")
    Integer countUnreadMessages(Long conversationId, Long userId);

    @Modifying
    @Transactional
    @Query(value = "UPDATE chats SET isRead = 1 WHERE conversationId = :conversationId AND senderId != :userId AND isRead = false", nativeQuery = true)
    Integer updateMessagesAsRead(Long conversationId, Long userId);

    List<ChatEntity> findAllByConversationIdAndIsActive(Long conversationId2, ActiveStatus y);

    /**
     * Count distinct conversations where this user has sent at least one message.
     * Used for Starter plan conversation limit tracking.
     * Excludes system auto-messages by checking senderId = the user (system messages have senderId = receiverId/other).
     */
    /**
     * Count distinct conversations where user has sent a REAL message (not auto-messages).
     * Excludes system auto-messages generated on interest send/accept.
     */
    @Query("SELECT COUNT(DISTINCT c.conversationId) FROM ChatEntity c WHERE c.senderId = :userId " +
           "AND c.message NOT LIKE 'I have liked your profile%' " +
           "AND c.message NOT LIKE 'Interest request approved%'")
    Long countDistinctConversationsBySenderId(Long userId);

    /**
     * Check if user has already sent a REAL message in this specific conversation.
     */
    @Query("SELECT COUNT(c) FROM ChatEntity c WHERE c.conversationId = :conversationId AND c.senderId = :userId " +
           "AND c.message NOT LIKE 'I have liked your profile%' " +
           "AND c.message NOT LIKE 'Interest request approved%'")
    Long countMessagesByConversationAndSender(Long conversationId, Long userId);
}
