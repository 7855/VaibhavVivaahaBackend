package com.uravugal.matrimony.controllers;

import com.uravugal.matrimony.dtos.PaginatedResultResponse;
import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.services.ServiceRequestService;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/service-request")
public class ServiceRequestController {

    @Autowired
    private ServiceRequestService serviceRequestService;

    @PostMapping("/create/{encodedUserId}")
    public ResultResponse create(
            @PathVariable String encodedUserId,
            @RequestBody Map<String, Object> body) {
        String requestType = body.get("requestType") != null ? body.get("requestType").toString() : null;
        String note = body.get("note") != null ? body.get("note").toString() : null;
        Long targetUserId = null;
        Object tu = body.get("targetUserId");
        if (tu != null) {
            try { targetUserId = Long.valueOf(tu.toString()); } catch (NumberFormatException ignored) {}
        }
        return serviceRequestService.createRequest(encodedUserId, requestType, note, targetUserId);
    }

    @GetMapping("/my/{encodedUserId}")
    public PaginatedResultResponse my(
            @PathVariable String encodedUserId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return serviceRequestService.getMyRequests(encodedUserId, page, size);
    }

    // Admin endpoints (secure with admin auth at gateway / interceptor as needed)
    @GetMapping("/admin/list")
    public PaginatedResultResponse adminList(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String requestType,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return serviceRequestService.adminListRequests(status, requestType, page, size);
    }

    @PostMapping("/admin/{id}/status")
    public ResultResponse adminUpdate(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        String status = (String) body.get("status");
        Object adminIdObj = body.get("adminId");
        Long adminId = adminIdObj != null ? Long.valueOf(adminIdObj.toString()) : null;
        return serviceRequestService.adminUpdateStatus(id, status, adminId);
    }
}
