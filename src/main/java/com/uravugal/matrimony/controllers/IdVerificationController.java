package com.uravugal.matrimony.controllers;

import com.uravugal.matrimony.dtos.PaginatedResultResponse;
import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.services.IdVerificationService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/id-verification")
@CrossOrigin(origins = "*")
public class IdVerificationController {

    @Autowired
    private IdVerificationService idVerificationService;

    @PostMapping("/upload/{encodedUserId}")
    public ResultResponse uploadDocument(
            @PathVariable String encodedUserId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentType") String documentType,
            @RequestParam(value = "documentNumber", required = false) String documentNumber) {
        return idVerificationService.uploadDocument(encodedUserId, file, documentType, documentNumber);
    }

    @GetMapping("/status/{encodedUserId}")
    public ResultResponse getStatus(@PathVariable String encodedUserId) {
        return idVerificationService.getStatus(encodedUserId);
    }

    @GetMapping("/admin/list")
    public PaginatedResultResponse adminList(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return idVerificationService.adminList(status, page, size);
    }

    @PostMapping("/admin/{id}/review")
    public ResultResponse adminReview(
            @PathVariable Long id,
            @RequestParam String status,
            @RequestParam(required = false) String rejectionReason,
            @RequestParam(required = false) Long adminId) {
        return idVerificationService.adminReview(id, status, rejectionReason, adminId);
    }
}