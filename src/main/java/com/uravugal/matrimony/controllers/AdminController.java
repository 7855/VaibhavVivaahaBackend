package com.uravugal.matrimony.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.uravugal.matrimony.dtos.AdminUserDetailDTO;
import com.uravugal.matrimony.dtos.PaginatedResultResponse;
import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.dtos.AdminApprovePaymentRequestDTO;
import com.uravugal.matrimony.enums.PaymentRequestStatus;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.CmsPage;
import com.uravugal.matrimony.models.TicketMessage;
import com.uravugal.matrimony.services.AdminService;
import com.uravugal.matrimony.services.DailyMatchScheduler;

import java.util.Map;

@RestController
@RequestMapping("/admin")
@CrossOrigin(origins = "*")
public class AdminController {

    @Autowired
    private AdminService adminService;

    @Autowired
    private DailyMatchScheduler dailyMatchScheduler;

    /**
     * F3 — Daily Match Alerts: manual trigger for QA / on-demand runs.
     * Bypasses the 8 AM IST cron and processes the same logic immediately.
     * Returns a summary: {eligible, sent, skipped, errors, ranAt}.
     */
    @PostMapping("/runDailyMatchNow")
    public ResultResponse runDailyMatchNow() {
        ResultResponse resp = new ResultResponse();
        try {
            Map<String, Object> summary = dailyMatchScheduler.runNow();
            resp.setCode(200);
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setMessage("Daily match scheduler executed");
            resp.setData(summary);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setStatus(ResponseStatus.FAILURE);
            resp.setMessage("Daily match scheduler failed: " + e.getMessage());
        }
        return resp;
    }

    @GetMapping("/users")
    public PaginatedResultResponse getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String userType,
            @RequestParam(required = false) String userStatus) {
        PaginatedResultResponse resp = new PaginatedResultResponse();
        try {
            resp = adminService.getAllUsersForAdmin(page, size, userType, userStatus);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @PutMapping("/users/{userId}/toggle-active")
    public ResultResponse toggleUserActiveStatus(@PathVariable Long userId) {
        ResultResponse resp = new ResultResponse();
        try {
            resp = adminService.toggleUserActiveStatus(userId);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @GetMapping("/users/{userId}")
    public ResultResponse getUserDetail(@PathVariable Long userId) {
        ResultResponse resp = new ResultResponse();
        try {
            resp = adminService.getUserDetailForAdmin(userId);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @PostMapping("/users")
    public ResultResponse createUser(@RequestBody AdminUserDetailDTO request) {
        ResultResponse resp = new ResultResponse();
        try {
            resp = adminService.createUserFromAdmin(request);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @PutMapping("/users/{userId}")
    public ResultResponse updateUser(@PathVariable Long userId, @RequestBody AdminUserDetailDTO request) {
        ResultResponse resp = new ResultResponse();
        try {
            resp = adminService.updateUserFromAdmin(userId, request);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @PostMapping("/payment/approve")
    public ResultResponse approvePayment(@RequestBody AdminApprovePaymentRequestDTO request) {
        ResultResponse resp = new ResultResponse();
        try {
            resp = adminService.approvePayment(request);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @PostMapping("/payment/reject")
    public ResultResponse rejectPayment(@RequestBody AdminApprovePaymentRequestDTO request) {
        ResultResponse resp = new ResultResponse();
        try {
            resp = adminService.rejectPayment(request);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    // ==================== DASHBOARD STATS ====================

    @GetMapping("/stats")
    public ResultResponse getDashboardStats() {
        ResultResponse resp = new ResultResponse();
        try {
            resp = adminService.getDashboardStats();
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @GetMapping("/stats/registrations")
    public ResultResponse getRegistrationTrends() {
        ResultResponse resp = new ResultResponse();
        try {
            resp = adminService.getRegistrationTrends();
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @GetMapping("/stats/subscriptions")
    public ResultResponse getSubscriptionDistribution() {
        ResultResponse resp = new ResultResponse();
        try {
            resp = adminService.getSubscriptionDistribution();
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    // ==================== USER BLOCK/UNBLOCK ====================

    @PutMapping("/users/{userId}/block")
    public ResultResponse blockUser(@PathVariable Long userId) {
        ResultResponse resp = new ResultResponse();
        try {
            resp = adminService.blockUser(userId);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @PutMapping("/users/{userId}/unblock")
    public ResultResponse unblockUser(@PathVariable Long userId) {
        ResultResponse resp = new ResultResponse();
        try {
            resp = adminService.unblockUser(userId);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    // ==================== PROFILE APPROVAL ====================

    @PutMapping("/users/{userId}/approve")
    public ResultResponse approveUser(@PathVariable Long userId) {
        ResultResponse resp = new ResultResponse();
        try {
            resp = adminService.approveUser(userId);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @PutMapping("/users/{userId}/reject")
    public ResultResponse rejectUser(@PathVariable Long userId, @RequestBody java.util.Map<String, String> body) {
        ResultResponse resp = new ResultResponse();
        try {
            String reason = body != null ? body.get("reason") : null;
            resp = adminService.rejectUser(userId, reason);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    // ==================== PAYMENT MANAGEMENT ====================

    @GetMapping("/payments")
    public PaginatedResultResponse getAllPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {
        PaginatedResultResponse resp = new PaginatedResultResponse();
        try {
            PaymentRequestStatus paymentStatus = status != null ? PaymentRequestStatus.valueOf(status) : null;
            resp = adminService.getAllPayments(page, size, paymentStatus);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @GetMapping("/payments/stats")
    public ResultResponse getPaymentStats() {
        ResultResponse resp = new ResultResponse();
        try {
            resp = adminService.getPaymentStats();
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    // ==================== SUBSCRIPTION MANAGEMENT ====================

    @GetMapping("/subscriptions/users")
    public PaginatedResultResponse getAllUserSubscriptions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PaginatedResultResponse resp = new PaginatedResultResponse();
        try {
            resp = adminService.getAllUserSubscriptions(page, size);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    // ==================== REPORTS ====================

    @GetMapping("/reports")
    public PaginatedResultResponse getAllReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PaginatedResultResponse resp = new PaginatedResultResponse();
        try {
            resp = adminService.getAllReports(page, size);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    // ==================== RESTRICTED FIELD REQUESTS ====================

    @GetMapping("/requests")
    public PaginatedResultResponse getAllRestrictedFieldRequests(
            @RequestParam(required = false) String fieldType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PaginatedResultResponse resp = new PaginatedResultResponse();
        try {
            resp = adminService.getAllRestrictedFieldRequests(fieldType, page, size);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    // ==================== VERIFICATION (Images + Horoscopes) ====================

    @GetMapping("/verification/images")
    public PaginatedResultResponse getPendingImageVerifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PaginatedResultResponse resp = new PaginatedResultResponse();
        try {
            resp = adminService.getPendingImageVerifications(page, size);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @PutMapping("/verification/images/{galleryId}/approve")
    public ResultResponse approveImage(@PathVariable Long galleryId) {
        ResultResponse resp = new ResultResponse();
        try {
            resp = adminService.approveImage(galleryId);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @PutMapping("/verification/images/{galleryId}/reject")
    public ResultResponse rejectImage(@PathVariable Long galleryId,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        ResultResponse resp = new ResultResponse();
        try {
            String reason = body != null ? body.get("reason") : null;
            resp = adminService.rejectImage(galleryId, reason);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    // ==================== AUDIT LOGS ====================

    @GetMapping("/audit-logs")
    public PaginatedResultResponse getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String search) {
        PaginatedResultResponse resp = new PaginatedResultResponse();
        try {
            resp = adminService.getAuditLogs(page, size, module, search);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    // ==================== CMS PAGES ====================

    @GetMapping("/cms")
    public ResultResponse getAllCmsPages() {
        ResultResponse resp = new ResultResponse();
        try {
            resp = adminService.getAllCmsPages();
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @GetMapping("/cms/{slug}")
    public ResultResponse getCmsPageBySlug(@PathVariable String slug) {
        ResultResponse resp = new ResultResponse();
        try {
            resp = adminService.getCmsPageBySlug(slug);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @PutMapping("/cms/{slug}")
    public ResultResponse updateCmsPage(@PathVariable String slug, @RequestBody CmsPage cmsPage) {
        ResultResponse resp = new ResultResponse();
        try {
            resp = adminService.updateCmsPage(slug, cmsPage);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    // ==================== SUPPORT TICKETS ====================

    @GetMapping("/support/tickets")
    public PaginatedResultResponse getSupportTickets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {
        PaginatedResultResponse resp = new PaginatedResultResponse();
        try {
            resp = adminService.getSupportTickets(page, size, status);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @GetMapping("/support/tickets/{ticketId}")
    public ResultResponse getTicketDetail(@PathVariable Long ticketId) {
        ResultResponse resp = new ResultResponse();
        try {
            resp = adminService.getTicketDetail(ticketId);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @PostMapping("/support/tickets/{ticketId}/reply")
    public ResultResponse replyToTicket(@PathVariable Long ticketId, @RequestBody TicketMessage message) {
        ResultResponse resp = new ResultResponse();
        try {
            resp = adminService.replyToTicket(ticketId, message);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @PutMapping("/support/tickets/{ticketId}/close")
    public ResultResponse closeTicket(@PathVariable Long ticketId) {
        ResultResponse resp = new ResultResponse();
        try {
            resp = adminService.closeTicket(ticketId);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    // ==================== NOTIFICATIONS (Admin Send) ====================

    @GetMapping("/notifications")
    public PaginatedResultResponse getAdminNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PaginatedResultResponse resp = new PaginatedResultResponse();
        try {
            resp = adminService.getAdminNotifications(page, size);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    // ==================== SETTINGS ====================

    @GetMapping("/settings")
    public ResultResponse getSettings() {
        ResultResponse resp = new ResultResponse();
        try {
            resp = adminService.getSettings();
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @PutMapping("/settings")
    public ResultResponse updateSettings(@RequestBody java.util.Map<String, Object> settings) {
        ResultResponse resp = new ResultResponse();
        try {
            resp = adminService.updateSettings(settings);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }
}
