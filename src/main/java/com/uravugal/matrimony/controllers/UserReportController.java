package com.uravugal.matrimony.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.dtos.ReportUserRequest;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.services.UserReportService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/userReport")
public class UserReportController {
    
    @Autowired
    private UserReportService userReportService;

    @GetMapping("/getAllReports")
    public ResultResponse getAllReports() {
        ResultResponse response = new ResultResponse();
        try {
            response = userReportService.getAllReports();
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    @PostMapping("/reportUser")
    public ResultResponse reportUser(@Valid @RequestBody ReportUserRequest userReport) {
        ResultResponse response = new ResultResponse();
        try {
            response = userReportService.reportUser(userReport);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    /**
     * Admin action on a reported user.
     * Body: { action: "DISMISS" | "WARN" | "SUSPEND_7D" | "SUSPEND_30D" | "BAN",
     *         adminId: 5, note: "optional" }
     */
    @PostMapping("/admin/{id}/action")
    public ResultResponse adminAction(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, Object> body) {
        ResultResponse response = new ResultResponse();
        try {
            String action = (String) body.get("action");
            Object adminIdObj = body.get("adminId");
            Long adminId = adminIdObj != null ? Long.valueOf(adminIdObj.toString()) : null;
            String note = (String) body.get("note");
            response = userReportService.adminAction(id, action, adminId, note);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }
}
