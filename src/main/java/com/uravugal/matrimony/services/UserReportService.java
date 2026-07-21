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

    @Autowired
    private com.uravugal.matrimony.repositories.UserRepository userRepository;

    @Autowired
    private com.uravugal.matrimony.repositories.BannedIdentifierRepository bannedIdentifierRepository;

    public ResultResponse getAllReports() {
        ResultResponse response = new ResultResponse();
        try {
            List<UserReport> reports = userReportRepository.findAllOrderByReportedAtDesc();

            // Enrich with reporter/reported display names so both admin report pages can show
            // real people instead of bare user IDs — same "return everything, enrich" pattern
            // used by the other admin list endpoints added this session.
            List<java.util.Map<String, Object>> enriched = new java.util.ArrayList<>();
            for (UserReport r : reports) {
                java.util.Map<String, Object> row = new java.util.HashMap<>();
                row.put("id", r.getId());
                row.put("reportedByUserId", r.getReportedByUserId());
                row.put("reportedUserId", r.getReportedUserId());
                row.put("reason", r.getReason());
                row.put("reportType", r.getReportType());
                row.put("reportedMessageId", r.getReportedMessageId());
                row.put("messageContent", r.getMessageContent());
                row.put("reportedAt", r.getReportedAt());
                row.put("status", r.getStatus());
                row.put("reviewedByAdminId", r.getReviewedByAdminId());
                row.put("reviewedAt", r.getReviewedAt());
                row.put("reviewNote", r.getReviewNote());

                try {
                    com.uravugal.matrimony.models.UserEntity reporter = userRepository.findById(r.getReportedByUserId()).orElse(null);
                    if (reporter != null) {
                        row.put("reporterName", ((reporter.getFirstName() == null ? "" : reporter.getFirstName()) + " " +
                                (reporter.getLastName() == null ? "" : reporter.getLastName())).trim());
                    }
                } catch (Exception ignored) {}
                try {
                    com.uravugal.matrimony.models.UserEntity reported = userRepository.findById(r.getReportedUserId()).orElse(null);
                    if (reported != null) {
                        row.put("reportedName", ((reported.getFirstName() == null ? "" : reported.getFirstName()) + " " +
                                (reported.getLastName() == null ? "" : reported.getLastName())).trim());
                    }
                } catch (Exception ignored) {}

                enriched.add(row);
            }

            response.setCode(200);
            response.setMessage("Reports retrieved successfully");
            response.setStatus(ResponseStatus.SUCCESS);
            response.setData(enriched);
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
            if (userReport.getReportedMessageId() != null) {
                reportData.setReportType("MESSAGE");
                reportData.setReportedMessageId(userReport.getReportedMessageId());
                reportData.setMessageContent(userReport.getMessageContent());
            }

            // Create and save report
            UserReport savedReport = userReportRepository.save(reportData);

            // Blocking is now an explicit per-report choice instead of an automatic side effect —
            // null defaults to true only for backward compatibility with any client that predates
            // this field. Guard against inserting a duplicate block row (blockUser() elsewhere
            // already checks this; the old code here never did, so reporting an already-blocked
            // user would throw on the unique constraint and surface as a generic 500).
            boolean shouldBlock = userReport.getBlockUser() == null || userReport.getBlockUser();
            boolean alreadyBlocked = blockedUserRepository
                    .findByBlockedByUserIdAndBlockedUserId(senderUserId, userReport.getReportedUserId()) != null;
            if (shouldBlock && !alreadyBlocked) {
                BlockedUser blockedUser = new BlockedUser();
                blockedUser.setBlockedByUserId(senderUserId);
                blockedUser.setBlockedUserId(userReport.getReportedUserId());
                blockedUserRepository.save(blockedUser);
            }

            response.setCode(200);
            response.setMessage(shouldBlock ? "User reported and blocked successfully" : "Report submitted successfully");
            response.setStatus(ResponseStatus.SUCCESS);
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("report", savedReport);
            data.put("blocked", shouldBlock);
            response.setData(data);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    /**
     * Admin action against a report: DISMISS / WARN / SUSPEND_7D / SUSPEND_30D / BAN
     */
    public ResultResponse adminAction(Long reportId, String action, Long adminId, String note) {
        ResultResponse response = new ResultResponse();
        try {
            UserReport report = userReportRepository.findById(reportId).orElse(null);
            if (report == null) {
                response.setCode(404);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("Report not found");
                return response;
            }
            com.uravugal.matrimony.models.UserEntity reportedUser = userRepository
                    .findById(report.getReportedUserId()).orElse(null);

            switch (action == null ? "" : action.toUpperCase()) {
                case "DISMISS":
                    report.setStatus("DISMISSED");
                    break;
                case "WARN":
                    report.setStatus("WARNED");
                    // TODO: send push notification to reportedUser
                    break;
                case "SUSPEND_7D":
                    if (reportedUser != null) {
                        reportedUser.setUserStatus(com.uravugal.matrimony.enums.ApprovalStatus.SUSPENDED);
                        reportedUser.setSuspendedUntil(java.time.LocalDateTime.now().plusDays(7));
                        userRepository.save(reportedUser);
                    }
                    report.setStatus("SUSPENDED");
                    break;
                case "SUSPEND_30D":
                    if (reportedUser != null) {
                        reportedUser.setUserStatus(com.uravugal.matrimony.enums.ApprovalStatus.SUSPENDED);
                        reportedUser.setSuspendedUntil(java.time.LocalDateTime.now().plusDays(30));
                        userRepository.save(reportedUser);
                    }
                    report.setStatus("SUSPENDED");
                    break;
                case "BAN":
                    if (reportedUser != null) {
                        reportedUser.setUserStatus(com.uravugal.matrimony.enums.ApprovalStatus.BANNED);
                        reportedUser.setSuspendedUntil(null);
                        // Also revoke JWT refresh token
                        reportedUser.setRefreshToken(null);
                        reportedUser.setRefreshTokenExpiry(null);
                        userRepository.save(reportedUser);

                        // Blacklist mobile + email so they can't re-register
                        try {
                            com.uravugal.matrimony.models.BannedIdentifier bi =
                                    new com.uravugal.matrimony.models.BannedIdentifier();
                            bi.setBannedUserId(reportedUser.getUserId());
                            bi.setMobile(reportedUser.getMobile());
                            bi.setEmail(reportedUser.getEmail());
                            bi.setBannedByAdminId(adminId);
                            bi.setReason(note != null ? note : "Permanently banned via report #" + reportId);
                            bi.setBannedAt(java.time.LocalDateTime.now());
                            bannedIdentifierRepository.save(bi);
                        } catch (Exception ex) {
                            System.err.println("Failed to blacklist identifiers: " + ex.getMessage());
                        }
                    }
                    report.setStatus("BANNED");
                    break;
                default:
                    response.setCode(400);
                    response.setStatus(ResponseStatus.FAILURE);
                    response.setMessage("Invalid action. Allowed: DISMISS, WARN, SUSPEND_7D, SUSPEND_30D, BAN");
                    return response;
            }

            report.setReviewedByAdminId(adminId);
            report.setReviewedAt(java.time.LocalDateTime.now());
            report.setReviewNote(note);
            userReportRepository.save(report);

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Action applied successfully");
            response.setData(report);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error: " + e.getMessage());
        }
        return response;
    }
}
