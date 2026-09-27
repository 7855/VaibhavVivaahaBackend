package com.uravugal.matrimony.controllers;

import java.util.Base64;

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

    @GetMapping("/chatlist/{encodedUserId}")
    public ResultResponse getUserChatListWithRecentMessage(@PathVariable String encodedUserId) {
        ResultResponse resp = new ResultResponse();
        try {
            String decodedId = new String(Base64.getDecoder().decode(encodedUserId));
            Long userId = Long.parseLong(decodedId);
            resp = conversationService.getUserChatListWithRecentMessage(userId);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    /**
     * Remove a conversation from the CALLER's chat list only.
     *
     * encodedUserId is required and is verified against the conversation's participants — the
     * old form took a bare conversation id with no identity at all, so anyone could delete any
     * two strangers' chat. Kept as the same path prefix plus the id so existing clients fail
     * loudly (404) rather than silently deleting under the old semantics.
     */
    @GetMapping("/inActiveConversationById/{id}/{encodedUserId}")
    public ResultResponse inActiveConversationById(@PathVariable Long id,
            @PathVariable String encodedUserId) {
        ResultResponse response = new ResultResponse();
        try {
            Long requesterId = Long.parseLong(new String(Base64.getDecoder().decode(encodedUserId)));
            response = conversationService.deleteConversationForUser(id, requesterId);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    @GetMapping("/getByUsers/{userOneId}/{userTwoId}")
    public ResultResponse getConversationByUsers(@PathVariable Long userOneId, @PathVariable Long userTwoId) {
        ResultResponse response = new ResultResponse();
        try {
            response = conversationService.getConversationByUsers(userOneId, userTwoId);
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
