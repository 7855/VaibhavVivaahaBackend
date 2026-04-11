package com.uravugal.matrimony.services;

import com.uravugal.matrimony.dtos.PaginatedResultResponse;
import com.uravugal.matrimony.dtos.PaginationData;
import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.Features;
import com.uravugal.matrimony.models.IdVerification;
import com.uravugal.matrimony.models.PlanFeatures;
import com.uravugal.matrimony.models.UserEntity;
import com.uravugal.matrimony.models.UserSubscriptions;
import com.uravugal.matrimony.repositories.FeaturesRepository;
import com.uravugal.matrimony.repositories.IdVerificationRepository;
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
 * Government ID Verified Badge service (simplified manual review).
 * Silver+ (plans 4, 5, 6).
 * User uploads a photo of a government ID; admin eyeballs it and approves.
 * We never store the full document number — only last 4 digits for audit.
 */
@Service
public class IdVerificationService {

    private static final String AWS_BASE_PATH = "idVerifications/";

    private static final Set<String> ALLOWED_DOC_TYPES = Set.of(
            "AADHAAR_CARD", "PAN_CARD", "VOTER_ID", "DRIVING_LICENSE", "PASSPORT"
    );

    @Autowired private IdVerificationRepository idVerificationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserSubscriptionsRepository userSubscriptionsRepository;
    @Autowired private FeaturesRepository featuresRepository;
    @Autowired private PlanFeaturesRepository planFeaturesRepository;
    @Autowired private S3FileUploadService s3UploadService;

    public ResultResponse uploadDocument(String encodedUserId, MultipartFile file,
                                         String documentType, String documentNumber) {
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

            // Plan gate — Silver+ via ID_VERIFIED_BADGE
            UserSubscriptions sub = userSubscriptionsRepository.findTopByUserIdOrderByCreatedAtDesc(userId);
            if (sub == null || sub.getSubscriptionPlanId() == 1L) {
                resp.setCode(403);
                resp.setStatus(ResponseStatus.FAILURE);
                resp.setMessage("PLAN_UPGRADE_REQUIRED");
                return resp;
            }
            Features feature = featuresRepository.findByCode("ID_VERIFIED_BADGE");
            if (feature == null) {
                resp.setCode(500);
                resp.setStatus(ResponseStatus.FAILURE);
                resp.setMessage("ID_VERIFIED_BADGE feature not seeded in DB");
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

            IdVerification pending = idVerificationRepository
                    .findFirstByUserIdAndStatusOrderByIdDesc(userId, "PENDING");
            if (pending != null) {
                resp.setCode(409);
                resp.setStatus(ResponseStatus.FAILURE);
                resp.setMessage("DUPLICATE_PENDING_REQUEST");
                resp.setData(pending);
                return resp;
            }

            String fileName = "id_" + System.currentTimeMillis() + "_"
                    + UUID.randomUUID().toString().substring(0, 6)
                    + "." + getFileExtension(file.getOriginalFilename());
            File tempDir = new File(System.getProperty("java.io.tmpdir"));
            File tempFile = new File(tempDir, fileName);
            file.transferTo(tempFile);
            String fileUrl = s3UploadService.uploadGalleryImage(tempFile, AWS_BASE_PATH + "user_" + userId);
            tempFile.delete();

            // Store only the last 4 digits — never the full document number
            String last4 = null;
            if (documentNumber != null && documentNumber.length() >= 4) {
                String clean = documentNumber.replaceAll("\\s+", "");
                last4 = clean.substring(Math.max(0, clean.length() - 4));
            }

            IdVerification iv = new IdVerification();
            iv.setUserId(userId);
            iv.setDocumentUrl(fileUrl);
            iv.setDocumentType(documentType);
            iv.setDocumentNumberLast4(last4);
            iv.setStatus("PENDING");
            IdVerification saved = idVerificationRepository.save(iv);

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
            IdVerification latest = idVerificationRepository
                    .findFirstByUserIdOrderByIdDesc(userId);

            Map<String, Object> data = new HashMap<>();
            data.put("idVerified", Boolean.TRUE.equals(user.getIdVerified()));
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
            Page<IdVerification> p;
            if (status != null && !status.isBlank()) {
                p = idVerificationRepository.findByStatusOrderByIdDesc(status, pr);
            } else {
                p = idVerificationRepository.findAllByOrderByIdDesc(pr);
            }

            List<Map<String, Object>> enriched = new ArrayList<>();
            for (IdVerification iv : p.getContent()) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", iv.getId());
                row.put("userId", iv.getUserId());
                row.put("documentUrl", iv.getDocumentUrl());
                row.put("documentType", iv.getDocumentType());
                row.put("documentNumberLast4", iv.getDocumentNumberLast4());
                row.put("status", iv.getStatus());
                row.put("rejectionReason", iv.getRejectionReason());
                row.put("reviewedByAdminId", iv.getReviewedByAdminId());
                row.put("reviewedAt", iv.getReviewedAt());
                row.put("createdAt", iv.getCreatedAt());

                try {
                    UserEntity u = userRepository.findById(iv.getUserId()).orElse(null);
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
            resp.setMessage("ID verifications fetched");
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
            IdVerification iv = idVerificationRepository.findById(id).orElse(null);
            if (iv == null) {
                resp.setCode(404);
                resp.setStatus(ResponseStatus.FAILURE);
                resp.setMessage("Submission not found");
                return resp;
            }

            iv.setStatus(newStatus);
            iv.setReviewedAt(LocalDateTime.now());
            if (adminId != null) iv.setReviewedByAdminId(adminId);
            if ("REJECTED".equals(newStatus)) {
                iv.setRejectionReason(rejectionReason);
            } else {
                iv.setRejectionReason(null);
            }
            idVerificationRepository.save(iv);

            try {
                UserEntity user = userRepository.findById(iv.getUserId()).orElse(null);
                if (user != null) {
                    user.setIdVerified("VERIFIED".equals(newStatus));
                    userRepository.save(user);
                }
            } catch (Exception flagErr) {
                System.out.println("IdVerificationService: failed to flip id_verified: "
                        + flagErr.getMessage());
            }

            resp.setCode(200);
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setMessage("Submission reviewed");
            resp.setData(iv);
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