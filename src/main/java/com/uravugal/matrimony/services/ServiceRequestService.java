package com.uravugal.matrimony.services;

import com.uravugal.matrimony.dtos.PaginatedResultResponse;
import com.uravugal.matrimony.dtos.PaginationData;
import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.Features;
import com.uravugal.matrimony.models.PlanFeatures;
import com.uravugal.matrimony.models.ServiceRequest;
import com.uravugal.matrimony.models.UserSubscriptions;
import com.uravugal.matrimony.repositories.FeaturesRepository;
import com.uravugal.matrimony.repositories.PlanFeaturesRepository;
import com.uravugal.matrimony.repositories.ServiceRequestRepository;
import com.uravugal.matrimony.repositories.UserSubscriptionsRepository;

import java.util.Base64;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class ServiceRequestService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "VOICE_CALL", "VIDEO_PROFILE", "FAMILY_LOGIN",
            "SPEAK_FAMILY", "DEDICATED_RM", "FAMILY_ASSISTED_MATCH"
    );

    @Autowired
    private ServiceRequestRepository serviceRequestRepository;

    @Autowired
    private UserSubscriptionsRepository userSubscriptionsRepository;

    @Autowired
    private FeaturesRepository featuresRepository;

    @Autowired
    private PlanFeaturesRepository planFeaturesRepository;

    @Autowired
    private com.uravugal.matrimony.repositories.UserRepository userRepository;

    public ResultResponse createRequest(String encodedUserId, String requestType, String note, Long targetUserId) {
        ResultResponse resp = new ResultResponse();
        try {
            if (requestType == null || !ALLOWED_TYPES.contains(requestType)) {
                resp.setCode(400);
                resp.setStatus(ResponseStatus.FAILURE);
                resp.setMessage("Invalid requestType. Allowed: " + ALLOWED_TYPES);
                return resp;
            }
            Long userId = Long.parseLong(new String(Base64.getDecoder().decode(encodedUserId)));

            // Plan gate
            UserSubscriptions sub = userSubscriptionsRepository.findTopByUserIdOrderByCreatedAtDesc(userId);
            if (sub == null || sub.getSubscriptionPlanId() == 1L) {
                resp.setCode(403);
                resp.setStatus(ResponseStatus.FAILURE);
                resp.setMessage("PLAN_UPGRADE_REQUIRED");
                return resp;
            }
            Features feature = featuresRepository.findByCode(requestType);
            if (feature == null) {
                resp.setCode(500);
                resp.setStatus(ResponseStatus.FAILURE);
                resp.setMessage("Feature code not configured: " + requestType);
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

            // Dedup — block duplicate PENDING requests from the same user
            ServiceRequest existing;
            if (targetUserId != null) {
                existing = serviceRequestRepository
                        .findFirstByUserIdAndRequestTypeAndTargetUserIdAndStatus(
                                userId, requestType, targetUserId, "PENDING");
            } else {
                existing = serviceRequestRepository
                        .findFirstByUserIdAndRequestTypeAndStatus(userId, requestType, "PENDING");
            }
            if (existing != null) {
                resp.setCode(409);
                resp.setStatus(ResponseStatus.FAILURE);
                resp.setMessage("DUPLICATE_PENDING_REQUEST");
                resp.setData(existing);
                return resp;
            }

            ServiceRequest sr = new ServiceRequest();
            sr.setUserId(userId);
            sr.setTargetUserId(targetUserId);
            sr.setRequestType(requestType);
            sr.setStatus("PENDING");
            sr.setNote(note);
            ServiceRequest saved = serviceRequestRepository.save(sr);

            resp.setCode(200);
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setMessage("Request submitted. Our team will reach out shortly.");
            resp.setData(saved);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setStatus(ResponseStatus.FAILURE);
            resp.setMessage("Error creating request: " + e.getMessage());
        }
        return resp;
    }

    public PaginatedResultResponse getMyRequests(String encodedUserId, Integer page, Integer size) {
        PaginatedResultResponse resp = new PaginatedResultResponse();
        try {
            Long userId = Long.parseLong(new String(Base64.getDecoder().decode(encodedUserId)));
            Page<ServiceRequest> p = serviceRequestRepository.findByUserIdOrderByIdDesc(
                    userId, PageRequest.of(page != null ? page : 0, size != null ? size : 10));
            resp.setCode(200);
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setMessage("My requests fetched");
            resp.setData(p.getContent());
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

    public PaginatedResultResponse adminListRequests(String status, String requestType, Integer page, Integer size) {
        PaginatedResultResponse resp = new PaginatedResultResponse();
        try {
            PageRequest pr = PageRequest.of(page != null ? page : 0, size != null ? size : 20);
            Page<ServiceRequest> p;
            if (status != null && !status.isBlank()) {
                p = serviceRequestRepository.findByStatusOrderByIdDesc(status, pr);
            } else if (requestType != null && !requestType.isBlank()) {
                p = serviceRequestRepository.findByRequestTypeOrderByIdDesc(requestType, pr);
            } else {
                p = serviceRequestRepository.findAllByOrderByIdDesc(pr);
            }

            // Enrich each row with requester + target user names/mobiles for admin UI
            java.util.List<java.util.Map<String, Object>> enriched = new java.util.ArrayList<>();
            for (ServiceRequest sr : p.getContent()) {
                java.util.Map<String, Object> row = new java.util.HashMap<>();
                row.put("id", sr.getId());
                row.put("userId", sr.getUserId());
                row.put("targetUserId", sr.getTargetUserId());
                row.put("requestType", sr.getRequestType());
                row.put("status", sr.getStatus());
                row.put("note", sr.getNote());
                row.put("assignedAdminId", sr.getAssignedAdminId());
                row.put("createdAt", sr.getCreatedAt());

                try {
                    com.uravugal.matrimony.models.UserEntity requester =
                            userRepository.findById(sr.getUserId()).orElse(null);
                    if (requester != null) {
                        row.put("requesterName",
                                ((requester.getFirstName() == null ? "" : requester.getFirstName()) + " " +
                                 (requester.getLastName() == null ? "" : requester.getLastName())).trim());
                        row.put("requesterMobile", requester.getMobile());
                        row.put("requesterProfileImage", requester.getProfileImage());
                    }
                } catch (Exception ignored) {}

                try {
                    if (sr.getTargetUserId() != null) {
                        com.uravugal.matrimony.models.UserEntity target =
                                userRepository.findById(sr.getTargetUserId()).orElse(null);
                        if (target != null) {
                            row.put("targetName",
                                    ((target.getFirstName() == null ? "" : target.getFirstName()) + " " +
                                     (target.getLastName() == null ? "" : target.getLastName())).trim());
                            row.put("targetMobile", target.getMobile());
                            row.put("targetProfileImage", target.getProfileImage());
                        }
                    }
                } catch (Exception ignored) {}

                enriched.add(row);
            }

            resp.setCode(200);
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setMessage("Service requests fetched");
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

    public ResultResponse adminUpdateStatus(Long id, String newStatus, Long adminId) {
        ResultResponse resp = new ResultResponse();
        try {
            Set<String> ok = Set.of("PENDING", "IN_PROGRESS", "DONE", "REJECTED");
            if (newStatus == null || !ok.contains(newStatus)) {
                resp.setCode(400);
                resp.setStatus(ResponseStatus.FAILURE);
                resp.setMessage("Invalid status. Allowed: " + ok);
                return resp;
            }
            ServiceRequest sr = serviceRequestRepository.findById(id).orElse(null);
            if (sr == null) {
                resp.setCode(404);
                resp.setStatus(ResponseStatus.FAILURE);
                resp.setMessage("Service request not found");
                return resp;
            }
            sr.setStatus(newStatus);
            if (adminId != null) sr.setAssignedAdminId(adminId);
            serviceRequestRepository.save(sr);
            resp.setCode(200);
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setMessage("Status updated");
            resp.setData(sr);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setStatus(ResponseStatus.FAILURE);
            resp.setMessage("Error: " + e.getMessage());
        }
        return resp;
    }
}
