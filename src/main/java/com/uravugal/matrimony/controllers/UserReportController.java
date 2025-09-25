package com.uravugal.matrimony.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.dtos.ReportUserRequest;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.services.UserReportService;

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
    public ResultResponse reportUser(@RequestBody ReportUserRequest userReport) {
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
}
