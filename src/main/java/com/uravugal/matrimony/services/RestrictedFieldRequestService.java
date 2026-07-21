package com.uravugal.matrimony.services;

import com.uravugal.matrimony.dtos.PaginatedResultResponse;
import com.uravugal.matrimony.dtos.PaginationData;
import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.models.Notification;
import com.uravugal.matrimony.models.RestrictedFieldRequest;
import com.uravugal.matrimony.models.UserEntity;
import com.uravugal.matrimony.models.UserDetailEntity;
import com.uravugal.matrimony.enums.ActiveStatus;
import com.uravugal.matrimony.enums.ApprovalStatus;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.repositories.NotificationRepository;
import com.uravugal.matrimony.repositories.RestrictedFieldRequestRepository;
import com.uravugal.matrimony.repositories.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RestrictedFieldRequestService {

    @Autowired
    private RestrictedFieldRequestRepository restrictedFieldRequestRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PushNotificationService pushNotificationService;

    public ResultResponse deleteRequest(Long requestedBy, Long requestedTo, String fieldType) {
        ResultResponse response = new ResultResponse();
        try {
            RestrictedFieldRequest request = restrictedFieldRequestRepository.findByRequestedByAndRequestedToAndFieldTypeAndIsActive(
                requestedBy, requestedTo, fieldType, ActiveStatus.Y);
            
            if (request == null) {
                response.setCode(404);
                response.setMessage("Request not found");
                response.setStatus(ResponseStatus.FAILURE);
                return response;
            }

            restrictedFieldRequestRepository.delete(request);
            
            response.setCode(200);
            response.setMessage("Request deleted successfully");
            response.setStatus(ResponseStatus.SUCCESS);
            return response;
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Error deleting request: " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
            return response;
        }
    }

    public ResultResponse getRequestsToIds(Long requestById, Long requestToId) {
        ResultResponse response = new ResultResponse();
        try {
            
            List<RestrictedFieldRequest> requests = restrictedFieldRequestRepository.findByRequestedByAndRequestedToAndIsActive(
                requestById, requestToId, ActiveStatus.Y);
            
            if (requests.isEmpty()) {
                response.setCode(404);
                response.setMessage("No requests found");
                response.setStatus(ResponseStatus.FAILURE);
                return response;
            }

            response.setCode(200);
            response.setMessage("Requests retrieved successfully");
            response.setStatus(ResponseStatus.SUCCESS);
            response.setData(requests);
            return response;
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Error retrieving requests: " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
            return response;
        }
    }

    public ResultResponse getRestrictedRequestsToId(String encodedId) {
        ResultResponse response = new ResultResponse();
        try {
            // Decode the Base64 encoded ID
            String decodedId = new String(Base64.getDecoder().decode(encodedId));
            Long userId = Long.parseLong(decodedId);
            System.out.println("Decoded ID: " + decodedId);
            List<RestrictedFieldRequest> requests = restrictedFieldRequestRepository.findByRequestedToAndIsActive(userId, ActiveStatus.Y);
            
            System.out.println("Requests: " + requests);
            if (requests.isEmpty()) {
                response.setCode(404);
                response.setMessage("No requests found");
                response.setStatus(ResponseStatus.FAILURE);
                return response;
            }

            // Create list to hold all user data maps
            List<Map<String, Object>> userDataList = new ArrayList<>();
            
            // Process each request
            for (RestrictedFieldRequest request : requests) {
                Map<String, Object> userData = new HashMap<>();
                
                // Get user data from the request
                UserEntity user = userRepository.findById(request.getRequestedBy()).orElse(null);
                if (user != null) {
                    userData.put("firstname", user.getFirstName());
                    userData.put("lastname", user.getLastName());
                    userData.put("age", user.getAge());
                    userData.put("location", user.getLocation());
                    userData.put("profileImage", user.getProfileImage());
                    userData.put("idVerified", Boolean.TRUE.equals(user.getIdVerified()));
                    userData.put("educationVerified", Boolean.TRUE.equals(user.getEducationVerified()));
                    userData.put("incomeVerified", Boolean.TRUE.equals(user.getIncomeVerified()));
                    
                    // Get additional details from UserDetailEntity
                    if (user.getUserDetail() != null && !user.getUserDetail().isEmpty()) {
                        UserDetailEntity detail = user.getUserDetail().get(0);
                        userData.put("degree", detail.getDegree());
                        userData.put("AnnualIncome", detail.getAnnualIncome());
                        userData.put("Occupation", detail.getOccupation());
                    }
                }
                
                userData.put("fieldType", request.getFieldType());
                userData.put("status", request.getStatus());
                userData.put("requestedTo", request.getRequestedTo());
                userData.put("requestedBy", request.getRequestedBy());
                userData.put("requestedAt", request.getCreatedAt());
                userData.put("requestId", request.getId());

                userDataList.add(userData);
            }

            response.setCode(200);
            response.setMessage("User data fetched successfully");
            response.setStatus(ResponseStatus.SUCCESS);
            response.setData(userDataList);
            return response;
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Error fetching user data: " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
            return response;
        }
    }

    public ResultResponse sendRestrictedFieldRequest(Long requestedBy, Long requestedTo, String fieldType) {
        ResultResponse response = new ResultResponse();
        try {
            // A DB-level unique constraint (if any) aside, existsByRequestedByAndRequestedToAndFieldType
            // used to block ANY existing row regardless of status — so once the owner declined a
            // request, that same requester could never ask again; every retry hit "Request already
            // exists" forever. Mirrors the same fix already applied to InterestRequestService: reuse
            // and reset a REJECTED row back to PENDING instead of permanently blocking on it.
            RestrictedFieldRequest existing = restrictedFieldRequestRepository
                    .findByRequestedByAndRequestedToAndFieldTypeAndIsActive(requestedBy, requestedTo, fieldType, ActiveStatus.Y);

            RestrictedFieldRequest savedRequest;
            if (existing != null) {
                if (existing.getStatus() == ApprovalStatus.PENDING || existing.getStatus() == ApprovalStatus.APPROVED) {
                    response.setCode(400);
                    response.setMessage("Request already exists");
                    response.setStatus(ResponseStatus.FAILURE);
                    return response;
                }
                // Previously REJECTED — allow asking again by resetting the same row to PENDING
                existing.setStatus(ApprovalStatus.PENDING);
                savedRequest = restrictedFieldRequestRepository.save(existing);
            } else {
                RestrictedFieldRequest request = new RestrictedFieldRequest();
                request.setRequestedBy(requestedBy);
                request.setRequestedTo(requestedTo);
                request.setFieldType(fieldType);
                request.setStatus(ApprovalStatus.PENDING);
                savedRequest = restrictedFieldRequestRepository.save(request);
            }

            // Create notification for the receiver
            Notification notification = new Notification();
            notification.setSenderId(requestedBy);
            notification.setReceiverId(requestedTo);
            notification.setMessage("You have received a request to view your " + fieldType);
            notification.setNotificationCategory(fieldType);
            notification.setTitle("Asking Permission");
            notificationRepository.save(notification);

            pushNotificationService.sendPushNotificationToUser(requestedTo, "🔑 Permission Request", "A member wants to view your " + fieldType + ". Approve or decline the request.");


            response.setCode(201);
            response.setMessage("Request sent successfully");
            response.setStatus(ResponseStatus.SUCCESS);
            response.setData(savedRequest);

        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Error sending request: " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    public ResultResponse getRestrictedRequestsById(String encodedId) {
        ResultResponse response = new ResultResponse();
        try {
            String decodedId = new String(Base64.getDecoder().decode(encodedId));
            Long userId = Long.parseLong(decodedId);
            List<RestrictedFieldRequest> requests = restrictedFieldRequestRepository.findByRequestedByAndIsActive(
                userId, ActiveStatus.Y
            );
              
            System.out.println("Requests: " + requests);
            if (requests.isEmpty()) {
                response.setCode(404);
                response.setMessage("No requests found");
                response.setStatus(ResponseStatus.FAILURE);
                return response;
            }

            // Create list to hold all user data maps
            List<Map<String, Object>> userDataList = new ArrayList<>();
            
            // Process each request
            for (RestrictedFieldRequest request : requests) {
                Map<String, Object> userData = new HashMap<>();
                
                // Get user data from the request
                UserEntity user = userRepository.findById(request.getRequestedTo()).orElse(null);
                if (user != null) {
                    userData.put("firstname", user.getFirstName());
                    userData.put("lastname", user.getLastName());
                    userData.put("age", user.getAge());
                    userData.put("location", user.getLocation());
                    userData.put("profileImage", user.getProfileImage());
                    userData.put("idVerified", Boolean.TRUE.equals(user.getIdVerified()));
                    userData.put("educationVerified", Boolean.TRUE.equals(user.getEducationVerified()));
                    userData.put("incomeVerified", Boolean.TRUE.equals(user.getIncomeVerified()));
                    
                    // Get additional details from UserDetailEntity
                    if (user.getUserDetail() != null && !user.getUserDetail().isEmpty()) {
                        UserDetailEntity detail = user.getUserDetail().get(0);
                        userData.put("degree", detail.getDegree());
                        userData.put("AnnualIncome", detail.getAnnualIncome());
                        userData.put("Occupation", detail.getOccupation());
                    }
                }
                
                userData.put("fieldType", request.getFieldType());
                userData.put("status", request.getStatus());
                userData.put("requestedTo", request.getRequestedTo());
                userData.put("requestedBy", request.getRequestedBy());
                userData.put("requestedAt", request.getCreatedAt());
                userData.put("requestId", request.getId());
                
                userDataList.add(userData);
            }

            response.setCode(200);
            response.setMessage("User data fetched successfully");
            response.setStatus(ResponseStatus.SUCCESS);
            response.setData(userDataList);
            return response;
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Error fetching user data: " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
            return response;
        }
    }


    public PaginatedResultResponse getReceivedRequests(String encodedId, Integer page, Integer size) {
        PaginatedResultResponse resp = new PaginatedResultResponse();
        try {
            String decodedId = new String(Base64.getDecoder().decode(encodedId));
            Long userId = Long.parseLong(decodedId);
            List<RestrictedFieldRequest> requests = restrictedFieldRequestRepository.findByRequestedToAndIsActive(
                userId, ActiveStatus.Y
            );
            
            if (requests.isEmpty()) {
                resp.setCode(404);
                resp.setMessage("No field requests received");
                resp.setStatus(ResponseStatus.FAILURE);
                return resp;
            }

            resp.setCode(200);
            resp.setMessage("Field requests fetched successfully");
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setData(requests);
            
            // PaginationData pagination = new PaginationData();
            // pagination.setTotalPages(requests.getTotalPages());
            // pagination.setTotalElements(requests.getTotalElements());
            // pagination.setCurrentPage(requests.getNumber());
            // pagination.setPageSize(requests.getSize());
            // resp.setPaginationData(pagination);
            
            return resp;
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Error fetching field requests: " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
            return resp;
        }
    }

    public ResultResponse updateRestrictedFieldStatus(String encodedId, ApprovalStatus status) {
        ResultResponse resp = new ResultResponse();
        try {
            // Decode the ID
            byte[] decodedBytes = Base64.getDecoder().decode(encodedId);
            String decodedId = new String(decodedBytes);
            Long requestId = Long.parseLong(decodedId);

            RestrictedFieldRequest request = restrictedFieldRequestRepository.findById(requestId)
                    .orElseThrow(() -> new RuntimeException("Restricted field request not found"));

            applyStatusUpdate(request, status);

            resp.setCode(200);
            resp.setMessage("Status updated successfully");
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setData(request);
            return resp;
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Error updating status: " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
            return resp;
        }
    }

    // Shared by the mobile-facing (base64 id) and admin-facing (plain id) status-update paths.
    private void applyStatusUpdate(RestrictedFieldRequest request, ApprovalStatus status) {
        request.setStatus(status);
        restrictedFieldRequestRepository.save(request);

        if (status == ApprovalStatus.APPROVED || status == ApprovalStatus.REJECTED) {
            UserEntity user = userRepository.findById(request.getRequestedBy()).orElse(null);
            boolean approved = status == ApprovalStatus.APPROVED;
            Notification notification = new Notification();
            notification.setSenderId(request.getRequestedTo());
            notification.setReceiverId(request.getRequestedBy());
            notification.setMessage((user != null ? user.getFirstName() + " " + user.getLastName() : "The member") + (approved
                    ? " has accepted your request to view " + request.getFieldType()
                    : " has declined your request to view " + request.getFieldType()));
            notification.setNotificationCategory(request.getFieldType());
            notification.setTitle(approved ? "Permission Accepted" : "Permission Declined");
            notificationRepository.save(notification);

            pushNotificationService.sendPushNotificationToUser(request.getRequestedBy(), "🔑 Permission Request", "Your request to view " + request.getFieldType() + " has been " + status);
        }
    }

    // ============================================================
    // ADMIN — used by adminpanel's /requests/mobile|images|horoscopes pages.
    // Mirrors ServiceRequestService.adminListRequests/adminUpdateStatus exactly.
    // ============================================================
    public PaginatedResultResponse adminListRequests(String fieldType, String status, Integer page, Integer size) {
        PaginatedResultResponse resp = new PaginatedResultResponse();
        try {
            PageRequest pr = PageRequest.of(page != null ? page : 0, size != null ? size : 20);
            ApprovalStatus statusEnum = null;
            if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)) {
                statusEnum = ApprovalStatus.valueOf(status.toUpperCase());
            }

            Page<RestrictedFieldRequest> p;
            if (fieldType != null && !fieldType.isBlank() && statusEnum != null) {
                p = restrictedFieldRequestRepository.findByFieldTypeAndStatus(fieldType, statusEnum, pr);
            } else if (fieldType != null && !fieldType.isBlank()) {
                p = restrictedFieldRequestRepository.findByFieldType(fieldType, pr);
            } else if (statusEnum != null) {
                p = restrictedFieldRequestRepository.findByStatus(statusEnum, pr);
            } else {
                p = restrictedFieldRequestRepository.findAllByOrderByIdDesc(pr);
            }

            List<Map<String, Object>> enriched = new ArrayList<>();
            for (RestrictedFieldRequest r : p.getContent()) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", r.getId());
                row.put("requestedBy", r.getRequestedBy());
                row.put("requestedTo", r.getRequestedTo());
                row.put("fieldType", r.getFieldType());
                row.put("status", r.getStatus());
                row.put("createdAt", r.getCreatedAt());

                try {
                    UserEntity requester = userRepository.findById(r.getRequestedBy()).orElse(null);
                    if (requester != null) {
                        row.put("requesterName", ((requester.getFirstName() == null ? "" : requester.getFirstName()) + " " +
                                (requester.getLastName() == null ? "" : requester.getLastName())).trim());
                        row.put("requesterMobile", requester.getMobile());
                        row.put("requesterProfileImage", requester.getProfileImage());
                    }
                } catch (Exception ignored) {}

                try {
                    UserEntity target = userRepository.findById(r.getRequestedTo()).orElse(null);
                    if (target != null) {
                        row.put("targetName", ((target.getFirstName() == null ? "" : target.getFirstName()) + " " +
                                (target.getLastName() == null ? "" : target.getLastName())).trim());
                        row.put("targetMobile", target.getMobile());
                        row.put("targetProfileImage", target.getProfileImage());
                    }
                } catch (Exception ignored) {}

                enriched.add(row);
            }

            resp.setCode(200);
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setMessage("Restricted field requests fetched");
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
            resp.setMessage("Error fetching restricted field requests: " + e.getMessage());
        }
        return resp;
    }

    public ResultResponse adminUpdateStatus(Long id, String status) {
        ResultResponse resp = new ResultResponse();
        try {
            RestrictedFieldRequest request = restrictedFieldRequestRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Restricted field request not found"));
            ApprovalStatus statusEnum = ApprovalStatus.valueOf(status.toUpperCase());
            applyStatusUpdate(request, statusEnum);

            resp.setCode(200);
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setMessage("Status updated successfully");
            resp.setData(request);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setStatus(ResponseStatus.FAILURE);
            resp.setMessage("Error updating status: " + e.getMessage());
        }
        return resp;
    }

}
