package com.uravugal.matrimony.services;

import com.uravugal.matrimony.dtos.PaginatedResultResponse;
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
            // Check if request already exists
            boolean exists = restrictedFieldRequestRepository.existsByRequestedByAndRequestedToAndFieldType(
                requestedBy, requestedTo, fieldType
            );
            
            if (exists) {
                response.setCode(400);
                response.setMessage("Request already exists");
                response.setStatus(ResponseStatus.FAILURE);
                return response;
            }

            // Create new restricted field request
            RestrictedFieldRequest request = new RestrictedFieldRequest();
            request.setRequestedBy(requestedBy);
            request.setRequestedTo(requestedTo);
            request.setFieldType(fieldType);
            request.setStatus(ApprovalStatus.PENDING);
            
            // Save the request
            RestrictedFieldRequest savedRequest = restrictedFieldRequestRepository.save(request);

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
            
            // Find the restricted field request
            RestrictedFieldRequest request = restrictedFieldRequestRepository.findById(requestId)
                    .orElseThrow(() -> new RuntimeException("Restricted field request not found"));
            
            // Update the status
            request.setStatus(status);
            request = restrictedFieldRequestRepository.save(request);
            
           
             if(status.toString().equals("APPROVED")){
                UserEntity user = userRepository.findById(request.getRequestedBy()).orElse(null);
                Notification notification = new Notification();
                notification.setSenderId(request.getRequestedTo());
                notification.setReceiverId(request.getRequestedBy());
                notification.setMessage(user.getFirstName()+" "+user.getLastName() + " has accepted your request to view " + request.getFieldType());
                notification.setNotificationCategory(request.getFieldType());
                notification.setTitle("Permission Accepted");
                notificationRepository.save(notification);

                pushNotificationService.sendPushNotificationToUser(request.getRequestedBy(), "🔑 Permission Request", "Your request to view " + request.getFieldType() + " has been " + status);                

             }
            // Prepare response
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

}
