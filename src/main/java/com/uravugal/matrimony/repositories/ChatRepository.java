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
}
