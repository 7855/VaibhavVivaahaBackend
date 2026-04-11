package com.uravugal.matrimony.services;

import com.uravugal.matrimony.dtos.PaginatedResultResponse;
import com.uravugal.matrimony.dtos.PaginationData;
import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.EducationVerification;
import com.uravugal.matrimony.models.Features;
import com.uravugal.matrimony.models.PlanFeatures;
import com.uravugal.matrimony.models.UserEntity;
import com.uravugal.matrimony.models.UserSubscriptions;
import com.uravugal.matrimony.repositories.EducationVerificationRepository;
import com.uravugal.matrimony.repositories.FeaturesRepository;
import com.uravugal.matrimony.repositories.PlanFeaturesRepository;
import com.uravugal.matrimony.repositories.UserRepository;
import com.uravugal.matrimony.repositories.UserSubscriptionsRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Education Verified Badge service.
 * Silver+ (plans 4, 5, 6). Manual admin review. Accepts photo uploads
 * of degree certificates, diplomas, mark sheets, or professional certs.
 */
@Service
public class EducationVerificationService {

    private static final String AWS_BASE_PATH = "educationVerifications/";

    private static final Set<String> ALLOWED_DOC_TYPES = Set.of(
            "DEGREE_CERTIFICATE", "DIPLOMA", "PROFESSIONAL_CERT", "MARK_SHEET", "OTHER"
    );

    @Autowired private EducationVerificationRepository educationVerificationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserSubscriptionsRepository userSubscriptionsRepository;
    @Autowired private FeaturesRepository featuresRepository;
    @Autowired private PlanFeaturesRepository planFeaturesRepository;
    @Autowired private S3FileUploadService s3UploadService;

    public ResultResponse uploadDocument(String encodedUserId, MultipartFile file,
                                         String documentType, String institutionName, String qualification) {
        ResultResponse resp = new ResultResponse();
        try {
            if (file == null || file.isEmpty()) {
                resp.setCode(400);
                resp.setStatus(ResponseStatus.FAILURE);
                resp.setMessage("No file provided");
                return resp;
            }
            if (documentType == null || !ALLOWED_DOC_TYPES.contains(documentType)) {
                resp.setCode(400);
                resp.setStatus(ResponseStatus.FAILURE);
                resp.setMessage("Invalid documentType. Allowed: " + ALLOWED_DOC_TYPES);
                return resp;
            }

            Long userId = Long.parseLong(new String(Base64.getDecoder().decode(encodedUserId)));
            UserEntity user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                resp.setCode(404);
                resp.setStatus(ResponseStatus.FAILURE);
                resp.setMessage("User not found");
                return resp;
            }

            // Plan gate — Silver+ via EDUCATION_VERIFIED_BADGE
            UserSubscriptions sub = userSubscriptionsRepository.findTopByUserIdOrderByCreatedAtDesc(userId);
            if (sub == null || sub.getSubscriptionPlanId() == 1L) {
                resp.setCode(403);
                resp.setStatus(ResponseStatus.FAILURE);
                resp.setMessage("PLAN_UPGRADE_REQUIRED");
                return resp;
            }
            Features feature = featuresRepository.findByCode("EDUCATION_VERIFIED_BADGE");
            if (feature == null) {
                resp.setCode(500);
                resp.setStatus(ResponseStatus.FAILURE);
                resp.setMessage("EDUCATION_VERIFIED_BADGE feature not seeded in DB");
                return resp;
            }
            PlanFeatures pf = planFeaturesRepository.findByFeatureIdAndSubscriptionPlanId(
                    feature.getId(), sub.getSubscriptionPlanId());
            if (pf == null) {
                resp.setCode(403);
                resp.setStatus(ResponseStatus.FAILURE);
                resp.setMessage("PLAN_UPGRADE_REQUIRED");
                return resp;
            }

            EducationVerification pending = educationVerificationRepository
                    .findFirstByUserIdAndStatusOrderByIdDesc(userId, "PENDING");
            if (pending != null) {
                resp.setCode(409);
                resp.setStatus(ResponseStatus.FAILURE);
                resp.setMessage("DUPLICATE_PENDING_REQUEST");
                resp.setData(pending);
                return resp;
            }

            String fileName = "edu_" + System.currentTimeMillis() + "_"
                    + UUID.randomUUID().toString().substring(0, 6)
                    + "." + getFileExtension(file.getOriginalFilename());
            File tempDir = new File(System.getProperty("java.io.tmpdir"));
            File tempFile = new File(tempDir, fileName);
            file.transferTo(tempFile);
            String fileUrl = s3UploadService.uploadGalleryImage(tempFile, AWS_BASE_PATH + "user_" + userId);
            tempFile.delete();

            EducationVerification ev = new EducationVerification();
            ev.setUserId(userId);
            ev.setDocumentUrl(fileUrl);
            ev.setDocumentType(documentType);
            ev.setInstitutionName(institutionName);
            ev.setQualification(qualification);
            ev.setStatus("PENDING");
            EducationVerification saved = educationVerificationRepository.save(ev);

            resp.setCode(200);
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setMessage("Document submitted. Our team will review it within 24 hours.");
            resp.setData(saved);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setStatus(ResponseStatus.FAILURE);
            resp.setMessage("Error uploading document: " + e.getMessage());
        }
        return resp;
    }

    public ResultResponse getStatus(String encodedUserId) {
        ResultResponse resp = new ResultResponse();
        try {
            Long userId = Long.parseLong(new String(Base64.getDecoder().decode(encodedUserId)));
            UserEntity user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                resp.setCode(404);
                resp.setStatus(ResponseStatus.FAILURE);
                resp.setMessage("User not found");
                return resp;
            }
            EducationVerification latest = educationVerificationRepository
                    .findFirstByUserIdOrderByIdDesc(userId);

            Map<String, Object> data = new HashMap<>();
            data.put("educationVerified", Boolean.TRUE.equals(user.getEducationVerified()));
            data.put("latestSubmission", latest);

            resp.setCode(200);
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setMessage("Status fetched");
            resp.setData(data);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setStatus(ResponseStatus.FAILURE);
            resp.setMessage("Error: " + e.getMessage());
        }
        return resp;
    }

    public PaginatedResultResponse adminList(String status, Integer page, Integer size) {
        PaginatedResultResponse resp = new PaginatedResultResponse();
        try {
            PageRequest pr = PageRequest.of(page != null ? page : 0, size != null ? size : 20);
            Page<EducationVerification> p;
            if (status != null && !status.isBlank()) {
                p = educationVerificationRepository.findByStatusOrderByIdDesc(status, pr);
            } else {
                p = educationVerificationRepository.findAllByOrderByIdDesc(pr);
            }

            List<Map<String, Object>> enriched = new ArrayList<>();
            for (EducationVerification ev : p.getContent()) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", ev.getId());
                row.put("userId", ev.getUserId());
                row.put("documentUrl", ev.getDocumentUrl());
                row.put("documentType", ev.getDocumentType());
                row.put("institutionName", ev.getInstitutionName());
                row.put("qualification", ev.getQualification());
                row.put("status", ev.getStatus());
                row.put("rejectionReason", ev.getRejectionReason());
                row.put("reviewedByAdminId", ev.getReviewedByAdminId());
                row.put("reviewedAt", ev.getReviewedAt());
                row.put("createdAt", ev.getCreatedAt());

                try {
                    UserEntity u = userRepository.findById(ev.getUserId()).orElse(null);
                    if (u != null) {
                        row.put("userName",
                                ((u.getFirstName() == null ? "" : u.getFirstName()) + " " +
                                 (u.getLastName() == null ? "" : u.getLastName())).trim());
                        row.put("userMobile", u.getMobile());
                        row.put("userEmail", u.getEmail());
                        row.put("userProfileImage", u.getProfileImage());
                    }
                } catch (Exception ignored) {}

                enriched.add(row);
            }

            resp.setCode(200);
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setMessage("Education verifications fetched");
            resp.setData(enriched);
            PaginationData pg = new PaginationData();
            pg.setTotalPages(p.getTotalPages());
            pg.setTotalElements(p.getTotalElements());
            pg.setCurrentPage(p.getNumber());
            pg.setPageSize(p.getSize());
            resp.setPaginationData(pg);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setStatus(ResponseStatus.FAILURE);
            resp.setMessage("Error: " + e.getMessage());
        }
        return resp;
    }

    public ResultResponse adminReview(Long id, String newStatus, String rejectionReason, Long adminId) {
        ResultResponse resp = new ResultResponse();
        try {
            Set<String> ok = Set.of("VERIFIED", "REJECTED", "PENDING");
            if (newStatus == null || !ok.contains(newStatus)) {
                resp.setCode(400);
                resp.setStatus(ResponseStatus.FAILURE);
                resp.setMessage("Invalid status. Allowed: " + ok);
                return resp;
            }
            EducationVerification ev = educationVerificationRepository.findById(id).orElse(null);
            if (ev == null) {
                resp.setCode(404);
                resp.setStatus(ResponseStatus.FAILURE);
                resp.setMessage("Submission not found");
                return resp;
            }

            ev.setStatus(newStatus);
            ev.setReviewedAt(LocalDateTime.now());
            if (adminId != null) ev.setReviewedByAdminId(adminId);
            if ("REJECTED".equals(newStatus)) {
                ev.setRejectionReason(rejectionReason);
            } else {
                ev.setRejectionReason(null);
            }
            educationVerificationRepository.save(ev);

            try {
                UserEntity user = userRepository.findById(ev.getUserId()).orElse(null);
                if (user != null) {
                    user.setEducationVerified("VERIFIED".equals(newStatus));
                    userRepository.save(user);
                }
            } catch (Exception flagErr) {
                System.out.println("EducationVerificationService: failed to flip education_verified: "
                        + flagErr.getMessage());
            }

            resp.setCode(200);
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setMessage("Submission reviewed");
            resp.setData(ev);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setStatus(ResponseStatus.FAILURE);
            resp.setMessage("Error: " + e.getMessage());
        }
        return resp;
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) return "jpg";
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }
}