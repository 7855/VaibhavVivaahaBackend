package com.uravugal.matrimony.controllers;

import com.uravugal.matrimony.dtos.PaginatedResultResponse;
import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.services.IncomeVerificationService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/income-verification")
@CrossOrigin(origins = "*")
public class IncomeVerificationController {

    @Autowired
    private IncomeVerificationService incomeVerificationService;

    /** User uploads a salary slip / ITR / offer letter PDF. */
    @PostMapping("/upload/{encodedUserId}")
    public ResultResponse uploadDocument(
            @PathVariable String encodedUserId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentType") String documentType,
            @RequestParam(value = "claimedAnnualIncome", required = false) String claimedAnnualIncome) {
        return incomeVerificationService.uploadDocument(encodedUserId, file, documentType, claimedAnnualIncome);
    }

    /** Returns latest submission + the user's incomeVerified flag. */
    @GetMapping("/status/{encodedUserId}")
    public ResultResponse getStatus(@PathVariable String encodedUserId) {
        return incomeVerificationService.getStatus(encodedUserId);
    }

    /** Admin: paginated list, optionally filtered by status. */
    @GetMapping("/admin/list")
    public PaginatedResultResponse adminList(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return incomeVerificationService.adminList(status, page, size);
    }

    /** Admin: approve or reject a submission. */
    @PostMapping("/admin/{id}/review")
    public ResultResponse adminReview(
            @PathVariable Long id,
            @RequestParam String status,
            @RequestParam(required = false) String rejectionReason,
            @RequestParam(required = false) Long adminId) {
        return incomeVerificationService.adminReview(id, status, rejectionReason, adminId);
    }
}