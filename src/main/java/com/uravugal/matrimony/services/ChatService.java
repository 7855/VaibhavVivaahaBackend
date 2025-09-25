package com.uravugal.matrimony.services;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// import com.uravugal.matrimony.dtos.ChatRequest;
import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ActiveStatus;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.ChatEntity;
import com.uravugal.matrimony.models.Conversation;
import com.uravugal.matrimony.repositories.ChatRepository;
import com.uravugal.matrimony.repositories.ConversationRepository;

@Service
public class ChatService {
    
    @Autowired
    private ChatRepository chatRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    public ResultResponse sendChatMessage(ChatEntity request) {
        ResultResponse response = new ResultResponse();
        try {
            Conversation conversation = conversationRepository.findById(request.getConversationId())
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

            ChatEntity chatMessage = new ChatEntity();
            chatMessage.setConversationId(conversation.getId());
            chatMessage.setSenderId(request.getSenderId());
            chatMessage.setMessage(request.getMessage());
            chatMessage.setIsRead(false);
            chatMessage.setIsActive(ActiveStatus.Y);

            chatRepository.save(chatMessage);

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Message sent successfully");
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error sending message: " + e.getMessage());
        }
        return response;
    }

    public ResultResponse getActiveChatsByConversation(Long conversationId) {
        ResultResponse response = new ResultResponse();
        try {
            List<ChatEntity> chatMessages = chatRepository.findAllByConversationIdAndIsActive(conversationId, ActiveStatus.Y);

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Chat messages fetched successfully.");
            response.setData(chatMessages);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching chat messages: " + e.getMessage());
        }
        return response;
    }

    public ResultResponse inActiveMessageById(Long id) {
        ResultResponse response = new ResultResponse();
        try {
            if(chatRepository.findById(id).isPresent()) {
                ChatEntity chatMessage = chatRepository.findById(id).get();
                chatMessage.setIsActive(ActiveStatus.N);
                chatRepository.save(chatMessage);
                
                response.setCode(200);
                response.setStatus(ResponseStatus.SUCCESS);
                response.setMessage("Chat messages inactivated successfully.");
            }else {
                response.setCode(404);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("Chat messages not found.");
            }

          
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching chat messages: " + e.getMessage());
        }
        return response;
    }

    public ResultResponse markMessagesAsRead(Long conversationId, Long senderId) {
        ResultResponse response = new ResultResponse();
        try {
            // Update messages as read
            Integer updatedCount = chatRepository.updateMessagesAsRead(conversationId, senderId);
            
            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Messages marked as read successfully.");
            response.setData(updatedCount); // Number of messages updated
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error marking messages as read: " + e.getMessage());
        }
        return response;
    }
}
