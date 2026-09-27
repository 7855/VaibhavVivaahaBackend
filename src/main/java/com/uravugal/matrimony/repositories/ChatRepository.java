package com.uravugal.matrimony.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.uravugal.matrimony.enums.ActiveStatus;
import com.uravugal.matrimony.models.ChatEntity;

@Repository
public interface ChatRepository extends JpaRepository<ChatEntity, Long> {

    @Query("SELECT COUNT(c) FROM ChatEntity c WHERE c.conversationId = :conversationId AND c.senderId != :userId AND c.isRead = false "
         + "AND (c.hiddenFromReceiver IS NULL OR c.hiddenFromReceiver = false)")
    Integer countUnreadMessages(Long conversationId, Long userId);

    /**
     * Same unread count as countUnreadMessages, batched across every conversation in one query —
     * the chat list previously called countUnreadMessages once per row in a loop (N+1: 60
     * conversations = 60 sequential queries just for unread badges). Returns [conversationId,
     * count] pairs only for conversations that actually have unread messages; conversations with
     * zero just won't appear in the result and the caller defaults them to 0.
     */
    @Query("SELECT c.conversationId, COUNT(c) FROM ChatEntity c " +
           "WHERE c.conversationId IN :conversationIds AND c.senderId != :userId AND c.isRead = false " +
           "AND (c.hiddenFromReceiver IS NULL OR c.hiddenFromReceiver = false) " +
           "GROUP BY c.conversationId")
    List<Object[]> countUnreadMessagesGrouped(@Param("conversationIds") List<Long> conversationIds, @Param("userId") Long userId);

    @Modifying
    @Transactional
    @Query(value = "UPDATE chats SET isRead = 1, isDelivered = 1 WHERE conversationId = :conversationId AND senderId != :userId AND isRead = false "
                 + "AND COALESCE(hidden_from_receiver, 0) = 0", nativeQuery = true)
    Integer updateMessagesAsRead(Long conversationId, Long userId);

    /**
     * [conversationId, senderId] for every message addressed to :userId that has not reached
     * their device yet — read BEFORE markDeliveredForReceiver so each sender can be told.
     */
    @Query(value = "SELECT DISTINCT c.conversationId, c.senderId FROM chats c "
                 + "JOIN conversations cv ON cv.id = c.conversationId "
                 + "WHERE (cv.user_one_id = :userId OR cv.user_two_id = :userId) AND c.senderId <> :userId "
                 + "AND COALESCE(c.isDelivered, 0) = 0 AND COALESCE(c.hidden_from_receiver, 0) = 0", nativeQuery = true)
    List<Object[]> findUndeliveredForReceiver(@Param("userId") Long userId);

    @Modifying
    @Transactional
    @Query(value = "UPDATE chats c JOIN conversations cv ON cv.id = c.conversationId SET c.isDelivered = 1 "
                 + "WHERE (cv.user_one_id = :userId OR cv.user_two_id = :userId) AND c.senderId <> :userId "
                 + "AND COALESCE(c.isDelivered, 0) = 0 AND COALESCE(c.hidden_from_receiver, 0) = 0", nativeQuery = true)
    Integer markDeliveredForReceiver(@Param("userId") Long userId);

    /**
     * Chat history for one conversation, OLDEST FIRST.
     *
     * The explicit ORDER BY is load-bearing. As a plain derived query this generated SQL with no
     * ORDER BY at all, so the row order was whatever the optimizer happened to produce — usually
     * insertion order, but not guaranteed, and it can shift once an index covers the WHERE clause.
     * chatscreen.tsx consumes this by calling .reverse() on the result (its FlatList is
     * `inverted`, so index 0 renders at the visual bottom), which ASSUMES a strict chronological
     * order — reversing an arbitrary order just produces a different arbitrary order. Symptom:
     * messages rendered out of sequence, e.g. a reply appearing above the message it answered.
     *
     * id is the tiebreaker because createdAt can collide: an auto-generated system message and a
     * user message written in the same request land on the same millisecond, and ordering by a
     * non-unique column alone is still non-deterministic.
     */
    @Query("SELECT c FROM ChatEntity c WHERE c.conversationId = :conversationId "
            + "AND c.isActive = :isActive ORDER BY c.createdAt ASC, c.id ASC")
    List<ChatEntity> findAllByConversationIdAndIsActive(@Param("conversationId") Long conversationId2,
            @Param("isActive") ActiveStatus y);

    /**
     * Same as above but from ONE participant's point of view: rows hidden from the receiver
     * (sent by someone this viewer has blocked) are dropped unless the viewer sent them.
     */
    @Query("SELECT c FROM ChatEntity c WHERE c.conversationId = :conversationId AND c.isActive = :isActive "
            + "AND (c.hiddenFromReceiver IS NULL OR c.hiddenFromReceiver = false OR c.senderId = :viewerId) "
            + "ORDER BY c.createdAt ASC, c.id ASC")
    List<ChatEntity> findVisibleByConversationForViewer(@Param("conversationId") Long conversationId,
            @Param("isActive") ActiveStatus isActive, @Param("viewerId") Long viewerId);

    /**
     * Incremental sync: only the rows NEWER than the last one the phone already has. This is the
     * app's single way of getting messages — on open (afterId 0), on every socket event, on
     * reconnect and on foreground — so a missed live push can never lose a message: the next
     * sync returns it. Ordered by id (the cursor), not createdAt.
     */
    @Query("SELECT c FROM ChatEntity c WHERE c.conversationId = :conversationId AND c.isActive = :isActive "
            + "AND c.id > :afterId "
            + "AND (c.hiddenFromReceiver IS NULL OR c.hiddenFromReceiver = false OR c.senderId = :viewerId) "
            + "ORDER BY c.id ASC")
    List<ChatEntity> findVisibleAfter(@Param("conversationId") Long conversationId,
            @Param("isActive") ActiveStatus isActive, @Param("viewerId") Long viewerId,
            @Param("afterId") Long afterId);

    /**
     * Tick state for the viewer's OWN messages, as two high-water marks. Reads and deliveries are
     * applied in bulk ("everything from this sender"), so "all of mine up to id N are read" is
     * exact — and it lets an incremental sync update ticks on OLD messages, which a
     * "rows after X" query alone would never return.
     */
    @Query("SELECT COALESCE(MAX(c.id), 0) FROM ChatEntity c WHERE c.conversationId = :conversationId "
            + "AND c.senderId = :senderId AND c.isRead = true")
    Long maxReadId(@Param("conversationId") Long conversationId, @Param("senderId") Long senderId);

    @Query("SELECT COALESCE(MAX(c.id), 0) FROM ChatEntity c WHERE c.conversationId = :conversationId "
            + "AND c.senderId = :senderId AND (c.isDelivered = true OR c.isRead = true)")
    Long maxDeliveredId(@Param("conversationId") Long conversationId, @Param("senderId") Long senderId);

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

    /**
     * Count any REAL messages in a conversation, from either side — excludes the auto-generated
     * "interest sent"/"interest approved" system messages. Used to decide whether canceling a
     * sent interest request should also delete the conversation it created (only safe when no
     * real conversation ever happened on top of it).
     */
    @Query("SELECT COUNT(c) FROM ChatEntity c WHERE c.conversationId = :conversationId " +
           "AND c.message NOT LIKE 'I have liked your profile%' " +
           "AND c.message NOT LIKE 'Interest request approved%'")
    Long countRealMessagesByConversationId(Long conversationId);

    List<ChatEntity> findAllByConversationId(Long conversationId);
}
