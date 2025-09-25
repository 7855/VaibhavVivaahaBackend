package com.uravugal.matrimony.services;

import com.uravugal.matrimony.dtos.ReportUserRequest;
import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.UserReport;
import com.uravugal.matrimony.models.BlockedUser;
import com.uravugal.matrimony.repositories.UserReportRepository;
import com.uravugal.matrimony.repositories.BlockedUserRepository;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class UserReportService {
    
    @Autowired
    private UserReportRepository userReportRepository;
    
    @Autowired
    private BlockedUserRepository blockedUserRepository;

    public ResultResponse getAllReports() {
        ResultResponse response = new ResultResponse();
        try {
            List<UserReport> reports = userReportRepository.findAllOrderByReportedAtDesc();
            response.setCode(200);
            response.setMessage("Reports retrieved successfully");
            response.setStatus(ResponseStatus.SUCCESS);
            response.setData(reports);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    public ResultResponse reportUser(ReportUserRequest userReport) {
        ResultResponse response = new ResultResponse();
        try {
            // Check if report already exists
            String decodedId = new String(Base64.getDecoder().decode(userReport.getReportedByUserId()));
            Long senderUserId = Long.parseLong(decodedId);

            if (userReportRepository.findByReportedByUserIdAndReportedUserId(
                    senderUserId, 
                    userReport.getReportedUserId()
            ) != null) {
                response.setCode(400);
                response.setMessage("User already reported");
                response.setStatus(ResponseStatus.FAILURE);
                return response;
            }
            UserReport reportData = new UserReport();
            reportData.setReportedByUserId(senderUserId);
            reportData.setReportedUserId(userReport.getReportedUserId());
            reportData.setReason(userReport.getReason());

            // Create and save report
            UserReport savedReport = userReportRepository.save(reportData);

            // Create block entry
            BlockedUser blockedUser = new BlockedUser();
            blockedUser.setBlockedByUserId(senderUserId);
            blockedUser.setBlockedUserId(userReport.getReportedUserId());
            blockedUserRepository.save(blockedUser);

            response.setCode(200);
            response.setMessage("User reported and blocked successfully");
            response.setStatus(ResponseStatus.SUCCESS);
            response.setData(savedReport);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }
}
