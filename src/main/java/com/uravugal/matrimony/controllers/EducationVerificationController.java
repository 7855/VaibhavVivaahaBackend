package com.uravugal.matrimony.controllers;

import com.uravugal.matrimony.dtos.PaginatedResultResponse;
import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.services.EducationVerificationService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/education-verification")
@CrossOrigin(origins = "*")
public class EducationVerificationController {

    @Autowired
    private EducationVerificationService educationVerificationService;

    @PostMapping("/upload/{encodedUserId}")
    public ResultResponse uploadDocument(
            @PathVariable String encodedUserId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentType") String documentType,
            @RequestParam(value = "institutionName", required = false) String institutionName,
            @RequestParam(value = "qualification", required = false) String qualification) {
        return educationVerificationService.uploadDocument(encodedUserId, file, documentType, institutionName, qualification);
    }

    @GetMapping("/status/{encodedUserId}")
    public ResultResponse getStatus(@PathVariable String encodedUserId) {
        return educationVerificationService.getStatus(encodedUserId);
    }

    @GetMapping("/admin/list")
    public PaginatedResultResponse adminList(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return educationVerificationService.adminList(status, page, size);
    }

    @PostMapping("/admin/{id}/review")
    public ResultResponse adminReview(
            @PathVariable Long id,
            @RequestParam String status,
            @RequestParam(required = false) String rejectionReason,
            @RequestParam(required = false) Long adminId) {
        return educationVerificationService.adminReview(id, status, rejectionReason, adminId);
    }
}