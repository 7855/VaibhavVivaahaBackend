package com.uravugal.matrimony.controllers;

import com.uravugal.matrimony.dtos.PaginatedResultResponse;
import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.InterestRequest;
import com.uravugal.matrimony.services.InterestRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.Base64;

@RestController
@RequestMapping("/interestRequest")
public class InterestRequestController {
    
    @Autowired
    private InterestRequestService interestRequestService;

    // Send interest request
    @PostMapping("/sendInterestRequest/{senderId}/{receiverId}")
    public ResultResponse sendInterestRequest(@PathVariable String senderId, @PathVariable Long receiverId) {
        ResultResponse response = new ResultResponse();
        try {
            InterestRequest request = new InterestRequest();
            String decodedId = new String(Base64.getDecoder().decode(senderId));
            Long senderUserId = Long.parseLong(decodedId);
            request.setInterestSend(senderUserId);
            request.setInterestReceived(receiverId);
            response = interestRequestService.createInterestRequest(request);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    @GetMapping("/getAcceptedInterestRequests/{encodedUserId}")
    public ResultResponse getAcceptedInterestRequests(@PathVariable String encodedUserId) {
        ResultResponse response = new ResultResponse();
        try {
            response = interestRequestService.getAcceptedInterestRequests(encodedUserId);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Error fetching accepted interest requests: " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    // Get all interest requests for a user
    @GetMapping("/getAllInterestRequests/{userId}")
    public PaginatedResultResponse getAllInterestRequests(@PathVariable Long userId, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        PaginatedResultResponse response = new PaginatedResultResponse();
        try {
            String encodedId = Base64.getEncoder().encodeToString(userId.toString().getBytes());
            response = interestRequestService.getAllInterestRequests(encodedId, page, size);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    // Get pending interest requests
    @GetMapping("/getPendingInterestRequests/{userId}")
    public PaginatedResultResponse getPendingInterestRequests(@PathVariable Long userId, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        PaginatedResultResponse response = new PaginatedResultResponse();
        try {
            String encodedId = Base64.getEncoder().encodeToString(userId.toString().getBytes());
            response = interestRequestService.getPendingInterestRequests(encodedId, page, size);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    // Update interest request status
    @PostMapping("/updateInterestRequestStatus/{id}/{status}")
    public ResultResponse updateInterestRequestStatus(@PathVariable Long id, @PathVariable String status) {
        ResultResponse response = new ResultResponse();
        try {
            response = interestRequestService.updateInterestRequestStatus(id, status);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    // Delete interest request
    @DeleteMapping("/deleteInterestRequest/{id}")
    public ResultResponse deleteInterestRequest(@PathVariable Long id) {
        ResultResponse response = new ResultResponse();
        try {
            response = interestRequestService.deleteInterestRequest(id);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    @GetMapping("/checkInterestStatus/{senderId}/{receiverId}")
    public ResultResponse getInterestRequestStatus(
        @PathVariable String senderId,
        @PathVariable Long receiverId) {
        return interestRequestService.getInterestRequestStatus(senderId, receiverId);
    }
}
