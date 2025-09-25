package com.uravugal.matrimony.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.services.ConversationService;

@RestController
@RequestMapping("/conversation")
public class ConversationController {
    
    @Autowired
    private ConversationService conversationService;

    @GetMapping("/chatlist/{userId}")
    public ResultResponse getUserChatListWithRecentMessage(@PathVariable Long userId) {
        ResultResponse resp = new ResultResponse();
        try {
            resp = conversationService.getUserChatListWithRecentMessage(userId);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @GetMapping("/inActiveConversationById/{id}")
    public ResultResponse inActiveConversationById(@PathVariable Long id) {
        ResultResponse response = new ResultResponse();
        try {
            response = conversationService.inActiveConversationById(id);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    @GetMapping("/getConversationStatus/{conversationId}")
    public ResultResponse getConversationStatus(@PathVariable Long conversationId) {
        ResultResponse response = new ResultResponse();
        try {
            response = conversationService.getConversationStatus(conversationId);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }
}
