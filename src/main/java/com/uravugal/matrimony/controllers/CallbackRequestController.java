package com.uravugal.matrimony.controllers;

import com.uravugal.matrimony.dtos.PaginatedResultResponse;
import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.services.CallbackRequestService;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/callback-request")
public class CallbackRequestController {

    @Autowired
    private CallbackRequestService callbackRequestService;

    @PostMapping("/create/{encodedUserId}")
    public ResultResponse create(
            @PathVariable String encodedUserId,
            @RequestBody Map<String, Object> body) {
        String name = body.get("name") != null ? body.get("name").toString() : null;
        String mobile = body.get("mobile") != null ? body.get("mobile").toString() : null;
        String email = body.get("email") != null ? body.get("email").toString() : null;
        String planInterested = body.get("planInterested") != null ? body.get("planInterested").toString() : null;
        String note = body.get("note") != null ? body.get("note").toString() : null;
        String bestTimeToCall = body.get("bestTimeToCall") != null ? body.get("bestTimeToCall").toString() : null;
        return callbackRequestService.createRequest(
                encodedUserId, name, mobile, email, planInterested, note, bestTimeToCall);
    }

    @GetMapping("/my/{encodedUserId}")
    public PaginatedResultResponse my(
            @PathVariable String encodedUserId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return callbackRequestService.getMyRequests(encodedUserId, page, size);
    }

    // Admin endpoints
    @GetMapping("/admin/list")
    public PaginatedResultResponse adminList(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return callbackRequestService.adminListRequests(status, page, size);
    }

    @PostMapping("/admin/{id}/status")
    public ResultResponse adminUpdate(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        String status = (String) body.get("status");
        Object adminIdObj = body.get("adminId");
        Long adminId = adminIdObj != null ? Long.valueOf(adminIdObj.toString()) : null;
        return callbackRequestService.adminUpdateStatus(id, status, adminId);
    }
}