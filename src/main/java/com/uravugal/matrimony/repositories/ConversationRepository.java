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

    /**
     * Chat list for one user.
     *
     * The deleted_by_user_one / deleted_by_user_two predicate hides a conversation ONLY from the
     * participant who deleted it. Deleting previously flipped the shared GenericEntity.isActive,
     * which removed the thread from the other person's list as well. COALESCE(...,0) because rows
     * created before those columns existed are NULL and must read as "not deleted".
     *
     * NOTE: do not add `--` comments inside this string. Hibernate's parser has previously
     * registered phantom parameters from them and failed the whole query at startup.
     */
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
                  AND NOT (COALESCE(ch.hidden_from_receiver, 0) = 1 AND ch.senderId <> :userId)
                ORDER BY ch.createdAt DESC
                LIMIT 1
            ) AS lastMessage,
            (
                SELECT ch.createdAt FROM chats ch
                WHERE ch.conversationId = c.id
                  AND NOT (COALESCE(ch.hidden_from_receiver, 0) = 1 AND ch.senderId <> :userId)
                ORDER BY ch.createdAt DESC
                LIMIT 1
            ) AS lastMessageTime,
            (
                SELECT ch.isRead FROM chats ch
                WHERE ch.conversationId = c.id
                  AND NOT (COALESCE(ch.hidden_from_receiver, 0) = 1 AND ch.senderId <> :userId)
                ORDER BY ch.createdAt DESC
                LIMIT 1
            ) AS isRead,
            c.status,
            CASE
                WHEN c.user_one_id = :userId THEN u2.profileImage
                ELSE u1.profileImage
            END AS profileImage,
            CASE WHEN c.user_one_id = :userId THEN COALESCE(u2.id_verified, FALSE) ELSE COALESCE(u1.id_verified, FALSE) END AS idVerified,
            CASE WHEN c.user_one_id = :userId THEN COALESCE(u2.education_verified, FALSE) ELSE COALESCE(u1.education_verified, FALSE) END AS educationVerified,
            CASE WHEN c.user_one_id = :userId THEN COALESCE(u2.income_verified, FALSE) ELSE COALESCE(u1.income_verified, FALSE) END AS incomeVerified,
            CASE WHEN c.user_one_id = :userId THEN u2.gender ELSE u1.gender END AS gender,
            (
                SELECT ch.senderId FROM chats ch
                WHERE ch.conversationId = c.id
                  AND NOT (COALESCE(ch.hidden_from_receiver, 0) = 1 AND ch.senderId <> :userId)
                ORDER BY ch.createdAt DESC
                LIMIT 1
            ) AS lastMessageSenderId
        FROM conversations c
        JOIN users u1 ON c.user_one_id = u1.userId
        JOIN users u2 ON c.user_two_id = u2.userId
        WHERE (:userId = c.user_one_id OR :userId = c.user_two_id) AND c.isActive = 'Y'
          AND NOT (
                (:userId = c.user_one_id AND COALESCE(c.deleted_by_user_one, 0) = 1)
             OR (:userId = c.user_two_id AND COALESCE(c.deleted_by_user_two, 0) = 1)
          )
        ORDER BY lastMessageTime DESC
    """, nativeQuery = true)
    List<Object[]> getUserChatList(@Param("userId") Long userId);

    /** Everyone :userId shares a conversation with — the audience for their presence changes. */
    @Query(value = "SELECT CASE WHEN c.user_one_id = :userId THEN c.user_two_id ELSE c.user_one_id END "
                 + "FROM conversations c WHERE c.user_one_id = :userId OR c.user_two_id = :userId", nativeQuery = true)
    List<Object> findPartnerIds(@Param("userId") Long userId);
    
    Optional<Conversation> findByUserOneAndUserTwo(UserEntity userOne, UserEntity userTwo);

    Conversation findByInitiatedBy(Long id);

    Conversation findByUserOneAndUserTwo(Long interestSend, Long interestReceived);
}
