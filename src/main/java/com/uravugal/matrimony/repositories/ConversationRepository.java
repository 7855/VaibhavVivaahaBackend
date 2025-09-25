package com.uravugal.matrimony.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.uravugal.matrimony.models.Conversation;
import com.uravugal.matrimony.models.UserEntity;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    @Query(value = """
        SELECT 
            c.id AS conversationId,
            CASE WHEN c.user_one_id = :userId THEN c.user_two_id ELSE c.user_one_id END AS otherUserId,
            CASE 
                WHEN c.user_one_id = :userId THEN CONCAT(u2.firstName, ' ', u2.lastName) 
                ELSE CONCAT(u1.firstName, ' ', u1.lastName) 
            END AS otherUserName,
            (
                SELECT ch.message FROM chats ch
                WHERE ch.conversationId = c.id
                ORDER BY ch.createdAt DESC
                LIMIT 1
            ) AS lastMessage,
            (
                SELECT ch.createdAt FROM chats ch
                WHERE ch.conversationId = c.id
                ORDER BY ch.createdAt DESC
                LIMIT 1
            ) AS lastMessageTime,
            (
                SELECT ch.isRead FROM chats ch
                WHERE ch.conversationId = c.id
                ORDER BY ch.createdAt DESC
                LIMIT 1
            ) AS isRead,
            c.status,
            CASE 
                WHEN c.user_one_id = :userId THEN u2.profileImage
                ELSE u1.profileImage
            END AS profileImage
        FROM conversations c
        JOIN users u1 ON c.user_one_id = u1.userId
        JOIN users u2 ON c.user_two_id = u2.userId
        WHERE (:userId = c.user_one_id OR :userId = c.user_two_id)
        ORDER BY lastMessageTime DESC
    """, nativeQuery = true)
    List<Object[]> getUserChatList(@Param("userId") Long userId);
    
    Optional<Conversation> findByUserOneAndUserTwo(UserEntity userOne, UserEntity userTwo);

    Conversation findByInitiatedBy(Long id);

    Conversation findByUserOneAndUserTwo(Long interestSend, Long interestReceived);
}
