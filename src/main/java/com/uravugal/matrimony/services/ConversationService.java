package com.uravugal.matrimony.services;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.uravugal.matrimony.enums.ActiveStatus;
import com.uravugal.matrimony.enums.ChatStatus;
import com.uravugal.matrimony.dtos.ChatListResponse;
import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.Conversation;
import com.uravugal.matrimony.repositories.ConversationRepository;
import com.uravugal.matrimony.repositories.ChatRepository;

@Service
public class ConversationService {
    
    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ChatRepository chatRepository;

    @Autowired
    private ChatService chatService;

    private static final String LOCKED_PREVIEW = "\uD83D\uDD12 New message \u2014 upgrade to read";

    private static boolean isSystemMessage(String m) {
        return m != null && (m.startsWith("I have liked your profile") || m.startsWith("Interest request approved"));
    }

/**
 * Coerce a native-query column to boolean without assuming a JDBC type.
 *
 * A bit(1) column does NOT come back as the same Java type everywhere: read directly it is
 * usually Boolean, but wrapped in a scalar subquery or a COALESCE the server can widen it to an
 * integer, and some driver/connection-property combinations hand back byte[] instead. A raw
 * (Boolean) cast therefore works on one database and throws ClassCastException on another —
 * which is exactly what happened here: `(Boolean) row[5]` for isRead threw
 * "Integer cannot be cast to Boolean" in production while passing locally, taking the WHOLE
 * chat-list endpoint down with a 500. Because this backend returns HTTP 200 with the error
 * only in the body's `code`, the app read `data` as empty and rendered "no conversations"
 * rather than an error — the failure was invisible end to end. The gender column had already
 * caused this same class of bug once (see the note at the end of the mapper below).
 */
private static boolean asBoolean(Object value) {
    if (value == null) return false;
    if (value instanceof Boolean b) return b;
    if (value instanceof Number n) return n.intValue() != 0;
    if (value instanceof byte[] bytes) return bytes.length > 0 && bytes[0] != 0;
    String s = value.toString().trim();
    return s.equals("1") || s.equalsIgnoreCase("true") || s.equalsIgnoreCase("Y");
}

public ResultResponse getUserChatListWithRecentMessage(Long userId) {
    ResultResponse response = new ResultResponse();
    try {
        List<Object[]> rawResults = conversationRepository.getUserChatList(userId);

        // Batch every conversation's unread count into one query instead of one query per row —
        // was a real N+1 (60 conversations = 60 sequential DB round-trips just for unread badges,
        // directly slowing down every load/refresh of this list as an account accumulates chats).
        List<Long> conversationIds = rawResults.stream()
                .map(row -> ((Number) row[0]).longValue())
                .collect(Collectors.toList());
        Map<Long, Integer> unreadCountByConversation = new HashMap<>();
        if (!conversationIds.isEmpty()) {
            for (Object[] r : chatRepository.countUnreadMessagesGrouped(conversationIds, userId)) {
                unreadCountByConversation.put(((Number) r[0]).longValue(), ((Number) r[1]).intValue());
            }
        }

        // A member without the MESSAGE entitlement cannot open a chat (the app shows an
        // upgrade wall), yet the list preview used to print the other member's last message in
        // full. Mask it — they get to know a message arrived, not what it says.
        final boolean canRead = chatService.canReadMessages(userId);

        List<ChatListResponse> chatList = rawResults.stream().map(row -> {
            Long conversationId = ((Number) row[0]).longValue();
            Long otherUserId = ((Number) row[1]).longValue();

            Integer unreadCount = unreadCountByConversation.getOrDefault(conversationId, 0);

            String lastMessage = (String) row[3];
            Long lastSenderId = row.length > 12 && row[12] != null ? ((Number) row[12]).longValue() : null;
            if (!canRead && lastMessage != null && !isSystemMessage(lastMessage)
                    && (lastSenderId == null || !lastSenderId.equals(userId))) {
                lastMessage = LOCKED_PREVIEW;
            }

            return new ChatListResponse(
                conversationId,                                  // conversationId
                otherUserId,                                     // otherUserId
                (String) row[2],                                 // otherUserName
                lastMessage,                                     // lastMessage (masked for free viewers)
                row[4] != null ? ((Timestamp) row[4]).toLocalDateTime() : null, // lastMessageTime
                asBoolean(row[5]),                               // isRead — see asBoolean()
                (String) row[6],                                 // status
                (String) row[7],                                 // profileImage
                unreadCount,                                     // unreadMessageCount
                row.length > 8 && asBoolean(row[8]),              // idVerified
                row.length > 9 && asBoolean(row[9]),              // educationVerified
                row.length > 10 && asBoolean(row[10]),            // incomeVerified
                row.length > 11 && row[11] != null ? row[11].toString() : null // gender — the driver
                // returns this single-character ENUM column as a Character, not a String, so a
                // direct (String) cast threw a ClassCastException that crashed the whole chat
                // list endpoint (500), which the frontend then silently showed as "No
                // conversations yet" instead of a real error.
            );
        }).collect(Collectors.toList());

        response.setCode(200);
        response.setStatus(ResponseStatus.SUCCESS);
        response.setMessage("Chat list fetched successfully.");
        response.setData(chatList);
    } catch (Exception e) {
        response.setCode(500);
        response.setStatus(ResponseStatus.FAILURE);
        response.setMessage("Error fetching chat list: " + e.getMessage());
        e.printStackTrace(); // Add this for better error logging
    }
    return response;
}

    /**
     * Hide a conversation from ONE participant's chat list.
     *
     * Replaces the previous behaviour, which had two problems:
     *  1. It set the shared isActive = N, so "delete" removed the thread from BOTH people's
     *     lists — a member could send abusive messages and then erase them from the recipient's
     *     view. Now only the caller's own side is hidden.
     *  2. It took a bare conversation id with no identity check, so any caller could delete any
     *     two strangers' conversation (IDOR). requesterId is now verified to be a participant.
     *
     * The `chats` rows are deliberately left untouched — message history is retained in full for
     * moderation, and a new message un-hides the thread for both sides (see ChatService).
     */
    public ResultResponse deleteConversationForUser(Long id, Long requesterId) {
        ResultResponse response = new ResultResponse();
        try {
            if (requesterId == null) {
                response.setCode(400);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("Requester id is required.");
                return response;
            }
            if (conversationRepository.findById(id).isPresent()) {
                Conversation conversation = conversationRepository.findById(id).get();

                if (!conversation.markDeletedFor(requesterId)) {
                    // Not a participant — do not confirm the conversation exists.
                    response.setCode(403);
                    response.setStatus(ResponseStatus.FAILURE);
                    response.setMessage("You are not a participant in this conversation.");
                    return response;
                }
                conversationRepository.save(conversation);

                response.setCode(200);
                response.setStatus(ResponseStatus.SUCCESS);
                response.setMessage("Conversation removed from your chat list.");
            }else {
                response.setCode(404);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("Conversation not found.");
            }

          
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error inactivating conversation: " + e.getMessage());
        }
        return response;
    }

    // Resolves the conversation between two users without needing its id upfront — the
    // frontend's "Chat Now" button only knows the other user's id (the conversation is created
    // automatically back when the interest was sent, see InterestRequestService), so it needs a
    // way to look up that already-existing conversation before opening the chat screen.
    public ResultResponse getConversationByUsers(Long userOneId, Long userTwoId) {
        ResultResponse response = new ResultResponse();
        try {
            Long minId = Math.min(userOneId, userTwoId);
            Long maxId = Math.max(userOneId, userTwoId);
            Conversation conversation = conversationRepository.findByUserOneAndUserTwo(minId, maxId);

            if (conversation == null) {
                response.setCode(404);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("No conversation exists between these users yet.");
                return response;
            }

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Success");
            response.setData(conversation);
            return response;
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching conversation: " + e.getMessage());
            return response;
        }
    }

    public ResultResponse getConversationStatus(Long conversationId) {
        ResultResponse response = new ResultResponse();
        try {
            Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));
            
            response.setCode(200);
            response.setMessage("Success");
            response.setStatus(ResponseStatus.SUCCESS);
            response.setData(conversation);
            return response;
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
            return response;
        }
    }

}
