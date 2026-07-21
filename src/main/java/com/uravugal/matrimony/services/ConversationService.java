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

        List<ChatListResponse> chatList = rawResults.stream().map(row -> {
            Long conversationId = ((Number) row[0]).longValue();
            Long otherUserId = ((Number) row[1]).longValue();

            Integer unreadCount = unreadCountByConversation.getOrDefault(conversationId, 0);

            return new ChatListResponse(
                conversationId,                                  // conversationId
                otherUserId,                                     // otherUserId
                (String) row[2],                                 // otherUserName
                (String) row[3],                                 // lastMessage
                row[4] != null ? ((Timestamp) row[4]).toLocalDateTime() : null, // lastMessageTime
                row[5] != null && (Boolean) row[5],             // isRead - direct boolean cast
                (String) row[6],                                 // status
                (String) row[7],                                 // profileImage
                unreadCount,                                     // unreadMessageCount
                row.length > 8 && row[8] != null && ((Number) row[8]).intValue() != 0,  // idVerified
                row.length > 9 && row[9] != null && ((Number) row[9]).intValue() != 0,  // educationVerified
                row.length > 10 && row[10] != null && ((Number) row[10]).intValue() != 0, // incomeVerified
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

    public ResultResponse inActiveConversationById(Long id) {
        ResultResponse response = new ResultResponse();
        try {
            if(conversationRepository.findById(id).isPresent()) {
                Conversation conversation = conversationRepository.findById(id).get();
                conversation.setIsActive(ActiveStatus.N);
                conversationRepository.save(conversation);
                
                response.setCode(200);
                response.setStatus(ResponseStatus.SUCCESS);
                response.setMessage("Conversation inactivated successfully.");
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
