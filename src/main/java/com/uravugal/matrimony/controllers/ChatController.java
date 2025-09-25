package com.uravugal.matrimony.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.ChatEntity;
import com.uravugal.matrimony.services.ChatService;

@RestController
@RequestMapping("/chat")
public class ChatController {
    
    @Autowired
    private ChatService chatService;

    @PostMapping("/send")
    public ResultResponse sendChatMessage(@RequestBody ChatEntity request) {
        ResultResponse response = new ResultResponse();
        try {
            response = chatService.sendChatMessage(request);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    @GetMapping("/conversation/{conversationId}")
    public ResultResponse getActiveChatsByConversation(@PathVariable Long conversationId) {
        ResultResponse response = new ResultResponse();
        try {
            response = chatService.getActiveChatsByConversation(conversationId);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    @GetMapping("/inActiveMessageById/{id}")
    public ResultResponse inActiveMessageById(@PathVariable Long id) {
        ResultResponse response = new ResultResponse();
        try {
            response = chatService.inActiveMessageById(id);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    @PostMapping("/mark-as-read/{conversationId}/{senderId}")
    public ResultResponse markMessagesAsRead(@PathVariable Long conversationId, @PathVariable Long senderId) {
        ResultResponse response = new ResultResponse();
        try {
            response = chatService.markMessagesAsRead(conversationId, senderId);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }
}
