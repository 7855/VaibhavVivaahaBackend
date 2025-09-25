package com.uravugal.matrimony.services;

import java.sql.Timestamp;
import java.util.List;
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

            List<ChatListResponse> chatList = rawResults.stream().map(row -> {
                Long conversationId = ((Number) row[0]).longValue();
                Long otherUserId = ((Number) row[1]).longValue();
                
                // Get unread message count for this conversation
                Integer unreadCount = chatRepository.countUnreadMessages(conversationId, userId);

                return new ChatListResponse(
                    conversationId,                                  // conversationId
                    otherUserId,                                     // otherUserId
                    (String) row[2],                                 // otherUserName
                    (String) row[3],                                 // lastMessage
                    row[4] != null ? ((Timestamp) row[4]).toLocalDateTime() : null, // lastMessageTime
                    row[5] != null && ((Number) row[5]).intValue() == 1,           // isRead
                    (String) row[6],                                 // status
                    (String) row[7],                                 // profileImage
                    unreadCount                                      // unreadMessageCount
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
