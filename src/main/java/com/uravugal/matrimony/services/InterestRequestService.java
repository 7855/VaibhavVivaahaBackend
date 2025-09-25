package com.uravugal.matrimony.services;

import com.uravugal.matrimony.dtos.MailboxUserDetail;
import com.uravugal.matrimony.dtos.PaginatedResultResponse;
import com.uravugal.matrimony.dtos.PaginationData;
import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.models.ChatEntity;
import com.uravugal.matrimony.models.Conversation;
import com.uravugal.matrimony.models.InterestRequest;
import com.uravugal.matrimony.models.Notification;
import com.uravugal.matrimony.models.UserEntity;
import com.uravugal.matrimony.models.UserDetailEntity;
import com.uravugal.matrimony.enums.ApprovalStatus;
import com.uravugal.matrimony.enums.ChatStatus;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.repositories.ChatRepository;
import com.uravugal.matrimony.repositories.ConversationRepository;
import com.uravugal.matrimony.repositories.InterestRequestRepository;
import com.uravugal.matrimony.repositories.NotificationRepository;
import com.uravugal.matrimony.repositories.UserDetailRepository;
import com.uravugal.matrimony.repositories.UserRepository;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class InterestRequestService {

    @Autowired
    private InterestRequestRepository interestRequestRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserDetailRepository userDetailRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ChatRepository chatRepository;

    @Autowired
    private PushNotificationService pushNotificationService;

    public PaginatedResultResponse getReceivedRequests(String encodedId, Integer page, Integer size) {
        PaginatedResultResponse resp = new PaginatedResultResponse();
        try {
            String decodedId = new String(Base64.getDecoder().decode(encodedId));
            Long userId = Long.parseLong(decodedId);
            Page<InterestRequest> requests = interestRequestRepository.findByInterestReceived(
                userId,
                PageRequest.of(page != null ? page : 0, size != null ? size : 10)
            );

            System.out.println("requests========================>"+requests.getContent());
            
            if (requests.isEmpty()) {
                resp.setCode(404);
                resp.setMessage("No interest requests received");
                resp.setStatus(ResponseStatus.FAILURE);
                return resp;
            }

            // Fetch user details for each request
            List<MailboxUserDetail> userDetails = requests.getContent().stream()
                .map(request -> {
                    MailboxUserDetail detail = new MailboxUserDetail();
                    UserEntity user = userRepository.findById(request.getInterestSend()).orElse(null);
                    UserDetailEntity userDetail = userDetailRepository.findByUserId(request.getInterestSend());
                    
                    if (user != null) {
                        detail.setUserId(user.getUserId());
                        detail.setFirstName(user.getFirstName());
                        detail.setLastName(user.getLastName());
                        detail.setProfileImage(user.getProfileImage());
                        
                        if (userDetail != null) {
                            detail.setAge(user.getDob() != null ? 
                                (int) java.time.Period.between(user.getDob(), java.time.LocalDate.now()).getYears() : null);
                            detail.setDegree(userDetail.getDegree());
                            detail.setAnnualIncome(userDetail.getAnnualIncome());
                            detail.setOccupation(userDetail.getOccupation());
                            detail.setLocation(userDetail.getPresentAddress());
                        }
                        detail.setInterestId(request.getId());
                    }
                    return detail;
                })
                .collect(Collectors.toList());

            resp.setCode(200);
            resp.setMessage("Interest requests fetched successfully");
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setData(userDetails);
            
            PaginationData pagination = new PaginationData();
            pagination.setTotalPages(requests.getTotalPages());
            pagination.setTotalElements(requests.getTotalElements());
            pagination.setCurrentPage(requests.getNumber());
            pagination.setPageSize(requests.getSize());
            resp.setPaginationData(pagination);
            
            return resp;
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Error fetching interest requests: " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
            return resp;
        }
    }

    public PaginatedResultResponse getPendingReceivedRequests(String encodedId, Integer page, Integer size) {
        PaginatedResultResponse resp = new PaginatedResultResponse();
        try {
            String decodedId = new String(Base64.getDecoder().decode(encodedId));
            Long userId = Long.parseLong(decodedId);
            Page<InterestRequest> requests = interestRequestRepository.findByInterestReceivedAndAcceptStatus(
                userId, ApprovalStatus.PENDING,
                PageRequest.of(page != null ? page : 0, size != null ? size : 10)
            );

            if (requests.isEmpty()) {
                resp.setCode(404);
                resp.setMessage("No pending interest requests received");
                resp.setStatus(ResponseStatus.FAILURE);
                return resp;
            }

            // Fetch user details for each request
            List<MailboxUserDetail> userDetails = requests.getContent().stream()
                .map(request -> {
                    MailboxUserDetail detail = new MailboxUserDetail();
                    UserEntity user = userRepository.findById(request.getInterestSend()).orElse(null);
                    UserDetailEntity userDetail = userDetailRepository.findByUserId(request.getInterestSend());
                    
                    if (user != null) {
                        detail.setUserId(user.getUserId());
                        detail.setFirstName(user.getFirstName());
                        detail.setLastName(user.getLastName());
                        detail.setProfileImage(user.getProfileImage());
                        
                        if (userDetail != null) {
                            detail.setAge(user.getDob() != null ? 
                                (int) java.time.Period.between(user.getDob(), java.time.LocalDate.now()).getYears() : null);
                            detail.setDegree(userDetail.getDegree());
                            detail.setAnnualIncome(userDetail.getAnnualIncome());
                            detail.setOccupation(userDetail.getOccupation());
                            detail.setLocation(userDetail.getPresentAddress());
                        }
                        detail.setInterestId(request.getId());
                    }
                    return detail;
                })
                .collect(Collectors.toList());

            resp.setCode(200);
            resp.setMessage("Pending interest requests fetched successfully");
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setData(userDetails);
            
            PaginationData pagination = new PaginationData();
            pagination.setTotalPages(requests.getTotalPages());
            pagination.setTotalElements(requests.getTotalElements());
            pagination.setCurrentPage(requests.getNumber());
            pagination.setPageSize(requests.getSize());
            resp.setPaginationData(pagination);
            
            return resp;
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Error fetching pending interest requests: " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
            return resp;
        }
    }

    public PaginatedResultResponse getAcceptedReceivedRequests(String encodedId, Integer page, Integer size) {
        PaginatedResultResponse resp = new PaginatedResultResponse();
        try {
            String decodedId = new String(Base64.getDecoder().decode(encodedId));
            Long userId = Long.parseLong(decodedId);
            Page<InterestRequest> requests = interestRequestRepository.findByInterestReceivedAndAcceptStatus(
                userId, ApprovalStatus.APPROVED,
                PageRequest.of(page != null ? page : 0, size != null ? size : 10)
            );

            if (requests.isEmpty()) {
                resp.setCode(404);
                resp.setMessage("No accepted interest requests received");
                resp.setStatus(ResponseStatus.FAILURE);
                return resp;
            }

            // Fetch user details for each request
            List<MailboxUserDetail> userDetails = requests.getContent().stream()
                .map(request -> {
                    MailboxUserDetail detail = new MailboxUserDetail();
                    UserEntity user = userRepository.findById(request.getInterestSend()).orElse(null);
                    UserDetailEntity userDetail = userDetailRepository.findByUserId(request.getInterestSend());
                    
                    if (user != null) {
                        detail.setUserId(user.getUserId());
                        detail.setFirstName(user.getFirstName());
                        detail.setLastName(user.getLastName());
                        detail.setProfileImage(user.getProfileImage());
                        
                        if (userDetail != null) {
                            detail.setAge(user.getDob() != null ? 
                                (int) java.time.Period.between(user.getDob(), java.time.LocalDate.now()).getYears() : null);
                            detail.setDegree(userDetail.getDegree());
                            detail.setAnnualIncome(userDetail.getAnnualIncome());
                            detail.setOccupation(userDetail.getOccupation());
                            detail.setLocation(userDetail.getPresentAddress());
                        }
                        detail.setInterestId(request.getId());
                    }
                    return detail;
                })
                .collect(Collectors.toList());

            resp.setCode(200);
            resp.setMessage("Accepted interest requests fetched successfully");
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setData(userDetails);
            
            PaginationData pagination = new PaginationData();
            pagination.setTotalPages(requests.getTotalPages());
            pagination.setTotalElements(requests.getTotalElements());
            pagination.setCurrentPage(requests.getNumber());
            pagination.setPageSize(requests.getSize());
            resp.setPaginationData(pagination);
            
            return resp;
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Error fetching accepted interest requests: " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
            return resp;
        }
    }

    public PaginatedResultResponse getSentRequests(String encodedId, Integer page, Integer size) {
        PaginatedResultResponse resp = new PaginatedResultResponse();
        try {
            String decodedId = new String(Base64.getDecoder().decode(encodedId));
            Long userId = Long.parseLong(decodedId);
            Page<InterestRequest> requests = interestRequestRepository.findByInterestSendAndAcceptStatus(
                userId, ApprovalStatus.PENDING,
                PageRequest.of(page != null ? page : 0, size != null ? size : 10)
            );
            
            if (requests.isEmpty()) {
                resp.setCode(404);
                resp.setMessage("No interest requests sent");
                resp.setStatus(ResponseStatus.FAILURE);
                return resp;
            }

            // Fetch user details for each request
            List<MailboxUserDetail> userDetails = requests.getContent().stream()
                .map(request -> {
                    MailboxUserDetail detail = new MailboxUserDetail();
                    UserEntity user = userRepository.findById(request.getInterestReceived()).orElse(null);
                    UserDetailEntity userDetail = userDetailRepository.findByUserId(request.getInterestReceived());
                    
                    if (user != null) {
                        detail.setUserId(user.getUserId());
                        detail.setFirstName(user.getFirstName());
                        detail.setLastName(user.getLastName());
                        detail.setProfileImage(user.getProfileImage());
                        
                        if (userDetail != null) {
                            detail.setAge(user.getDob() != null ? 
                                (int) java.time.Period.between(user.getDob(), java.time.LocalDate.now()).getYears() : null);
                            detail.setDegree(userDetail.getDegree());
                            detail.setAnnualIncome(userDetail.getAnnualIncome());
                            detail.setOccupation(userDetail.getOccupation());
                            detail.setLocation(userDetail.getPresentAddress());
                        }
                        detail.setInterestId(request.getId());
                    }
                    return detail;
                })
                .collect(Collectors.toList());

            resp.setCode(200);
            resp.setMessage("Sent interest requests fetched successfully");
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setData(userDetails);
            
            PaginationData pagination = new PaginationData();
            pagination.setTotalPages(requests.getTotalPages());
            pagination.setTotalElements(requests.getTotalElements());
            pagination.setCurrentPage(requests.getNumber());
            pagination.setPageSize(requests.getSize());
            resp.setPaginationData(pagination);
            
            return resp;
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Error fetching sent interest requests: " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
            return resp;
        }
    }

    public PaginatedResultResponse getRejectedReceivedRequests(String encodedId, Integer page, Integer size) {
        PaginatedResultResponse resp = new PaginatedResultResponse();
        try {
            String decodedId = new String(Base64.getDecoder().decode(encodedId));
            Long userId = Long.parseLong(decodedId);
            Page<InterestRequest> requests = interestRequestRepository.findByInterestReceivedAndAcceptStatus(
                userId, ApprovalStatus.REJECTED,
                PageRequest.of(page != null ? page : 0, size != null ? size : 10)
            );

            if (requests.isEmpty()) {
                resp.setCode(404);
                resp.setMessage("No rejected interest requests received");
                resp.setStatus(ResponseStatus.FAILURE);
                return resp;
            }

            // Fetch user details for each request
            List<MailboxUserDetail> userDetails = requests.getContent().stream()
                .map(request -> {
                    MailboxUserDetail detail = new MailboxUserDetail();
                    UserEntity user = userRepository.findById(request.getInterestSend()).orElse(null);
                    UserDetailEntity userDetail = userDetailRepository.findByUserId(request.getInterestSend());
                    
                    if (user != null) {
                        detail.setUserId(user.getUserId());
                        detail.setFirstName(user.getFirstName());
                        detail.setLastName(user.getLastName());
                        detail.setProfileImage(user.getProfileImage());
                        
                        if (userDetail != null) {
                            detail.setAge(user.getDob() != null ? 
                                (int) java.time.Period.between(user.getDob(), java.time.LocalDate.now()).getYears() : null);
                            detail.setDegree(userDetail.getDegree());
                            detail.setAnnualIncome(userDetail.getAnnualIncome());
                            detail.setOccupation(userDetail.getOccupation());
                            detail.setLocation(userDetail.getPresentAddress());
                        }
                        detail.setInterestId(request.getId());
                    }
                    return detail;
                })
                .collect(Collectors.toList());

            resp.setCode(200);
            resp.setMessage("Rejected interest requests fetched successfully");
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setData(userDetails);
            
            PaginationData pagination = new PaginationData();
            pagination.setTotalPages(requests.getTotalPages());
            pagination.setTotalElements(requests.getTotalElements());
            pagination.setCurrentPage(requests.getNumber());
            pagination.setPageSize(requests.getSize());
            resp.setPaginationData(pagination);
            
            return resp;
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Error fetching rejected interest requests: " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
            return resp;
        }
    }

    public ResultResponse updateInterestRequestStatus(Long id, String approvalStatus) {
        ResultResponse resp = new ResultResponse();
        try {
            InterestRequest request = interestRequestRepository.findById(id)
                .orElse(null);

                if(request == null) {
                    resp.setCode(404);
                    resp.setMessage("Interest request not found");
                    resp.setStatus(ResponseStatus.FAILURE);
                    return resp;
                }   
            ApprovalStatus status = ApprovalStatus.valueOf(approvalStatus);
            request.setAcceptStatus(status);
            interestRequestRepository.save(request);

            if(status == ApprovalStatus.APPROVED) {
                System.out.println("getId: " + request.getId());
                System.out.println("getInterestSend: " + request.getInterestSend());
                System.out.println("getInterestReceived: " + request.getInterestReceived());
                
                Conversation convo = conversationRepository.findByUserOneAndUserTwo(request.getInterestSend(), request.getInterestReceived());
                System.out.println("Conversation: " + convo);
                if(convo != null) {
                    System.out.println("Conversation not null");
                    convo.setStatus(ChatStatus.ACCEPTED);
                    conversationRepository.save(convo);
                    System.out.println("Conversation saved");

                    ChatEntity chat = new ChatEntity();
                    chat.setConversationId(convo.getId());
                    chat.setSenderId(request.getInterestReceived());
                    chat.setMessage("Interest request approved. I have liked your profile too. Let's discuss further details.");
                    chatRepository.save(chat);
                    System.out.println("Chat saved");
                }
            }

            resp.setCode(200);
            resp.setMessage("Approval status updated successfully");
            resp.setStatus(ResponseStatus.SUCCESS);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Error updating approval status: " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
     
        return resp;
    }

    public ResultResponse deleteInterestRequest(Long id) {
        ResultResponse resp = new ResultResponse();
        try {
            InterestRequest request = interestRequestRepository.findById(id)
                .orElse(null);

            if(request == null) {
                resp.setCode(404);
                resp.setMessage("Interest request not found");
                resp.setStatus(ResponseStatus.FAILURE);
                return resp;
            }

            interestRequestRepository.delete(request);
            resp.setCode(200);
            resp.setMessage("Interest request deleted successfully");
            resp.setStatus(ResponseStatus.SUCCESS);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Error deleting interest request: " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    public PaginatedResultResponse getAllInterestRequests(String encodedId, Integer page, Integer size) {
        PaginatedResultResponse response = new PaginatedResultResponse();
        try {
            String decodedId = new String(Base64.getDecoder().decode(encodedId));
            Long userId = Long.parseLong(decodedId);
            Page<InterestRequest> requests = interestRequestRepository.findByInterestReceived(
                userId,
                PageRequest.of(page != null ? page : 0, size != null ? size : 10)
            );

            if (requests.isEmpty()) {
                response.setCode(404);
                response.setMessage("No interest requests found");
                response.setStatus(ResponseStatus.FAILURE);
                return response;
            }

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("All interest requests fetched successfully");
            response.setData(requests.getContent());

            PaginationData pagination = new PaginationData();
            pagination.setTotalPages(requests.getTotalPages());
            pagination.setTotalElements(requests.getTotalElements());
            pagination.setCurrentPage(requests.getNumber());
            pagination.setPageSize(requests.getSize());
            response.setPaginationData(pagination);

        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching interest requests: " + e.getMessage());
        }
        return response;
    }

    public PaginatedResultResponse getPendingInterestRequests(String encodedId, Integer page, Integer size) {
        PaginatedResultResponse response = new PaginatedResultResponse();
        try {
            String decodedId = new String(Base64.getDecoder().decode(encodedId));
            Long userId = Long.parseLong(decodedId);
            Page<InterestRequest> requests = interestRequestRepository.findByInterestReceivedAndAcceptStatus(
                userId,
                ApprovalStatus.PENDING,
                PageRequest.of(page != null ? page : 0, size != null ? size : 10)
            );

            if (requests.isEmpty()) {
                response.setCode(404);
                response.setMessage("No pending interest requests found");
                response.setStatus(ResponseStatus.FAILURE);
                return response;
            }

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Pending interest requests fetched successfully");
            response.setData(requests.getContent());

            PaginationData pagination = new PaginationData();
            pagination.setTotalPages(requests.getTotalPages());
            pagination.setTotalElements(requests.getTotalElements());
            pagination.setCurrentPage(requests.getNumber());
            pagination.setPageSize(requests.getSize());
            response.setPaginationData(pagination);

        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching pending interest requests: " + e.getMessage());
        }
        return response;
    }

    public ResultResponse createInterestRequest(InterestRequest request) {
        ResultResponse resp = new ResultResponse();
        try {
            // Check if request already exists
            boolean exists = interestRequestRepository.existsByInterestSendAndInterestReceived(
                    request.getInterestSend(), request.getInterestReceived());

            if (exists) {
                resp.setCode(400);
                resp.setMessage("Interest request already exists");
                resp.setStatus(ResponseStatus.FAILURE);
                return resp;
            }

            // Set default status to PENDING
            request.setAcceptStatus(ApprovalStatus.PENDING);

            InterestRequest savedRequest = interestRequestRepository.save(request);

            pushNotificationService.sendPushNotificationToUser(request.getInterestReceived(), "You’ve Received an Interest", "The user expressed in your profileSomeone has expressed interest in your profile. Check now to see who it is!");
            // Create notification for the receiver
            Notification notification = new Notification();
            notification.setSenderId(request.getInterestSend());
            notification.setReceiverId(request.getInterestReceived());
            notification.setMessage("Expressed interest in your profile");
            notification.setNotificationCategory("INTEREST");
            notification.setTitle("Interest Expressed");
            notificationRepository.save(notification);

            // Create conversation
            Conversation conversation = new Conversation();
            conversation.setUserOne(request.getInterestSend());
            conversation.setUserTwo(request.getInterestReceived());
            conversation.setStatus(ChatStatus.PENDING);
            conversation.setInitiatedBy(request.getInterestSend());
            Conversation savedConversation = conversationRepository.save(conversation);

            // Create chat entity
            ChatEntity chat = new ChatEntity();
            chat.setConversationId(savedConversation.getId());
            chat.setSenderId(request.getInterestSend());
            chat.setMessage(
                    "I have liked your profile. For further information discussion, please approve my request.");
            chatRepository.save(chat);

            resp.setCode(201);
            resp.setMessage("Interest request created successfully");
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setData(savedRequest);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Error creating interest request: " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    public ResultResponse getAcceptedInterestRequests(String encodedUserId) {
        ResultResponse response = new ResultResponse();
        try {
            String decodedUserId = new String(Base64.getDecoder().decode(encodedUserId));
            Long userId = Long.parseLong(decodedUserId);
            
            // Get both received and sent accepted interest requests
            List<InterestRequest> receivedRequests = interestRequestRepository.findByInterestReceivedAndAcceptStatus(
                userId, ApprovalStatus.APPROVED
            );
            List<InterestRequest> sentRequests = interestRequestRepository.findByInterestSendAndAcceptStatus(
                userId, ApprovalStatus.APPROVED
            );

            List<MailboxUserDetail> userDetails = new ArrayList<>();
            
            // Add received requests
            for (InterestRequest request : receivedRequests) {
                MailboxUserDetail detail = new MailboxUserDetail();
                UserEntity user = userRepository.findById(request.getInterestSend()).orElse(null);
                UserDetailEntity userDetail = userDetailRepository.findByUserId(request.getInterestSend());
                
                if (user != null && userDetail != null) {
                    detail.setUserId(user.getUserId());
                    detail.setFirstName(user.getFirstName());
                    detail.setLastName(user.getLastName());
                    detail.setProfileImage(user.getProfileImage());
                    
                    detail.setAge(user.getDob() != null ? 
                        (int) java.time.Period.between(user.getDob(), java.time.LocalDate.now()).getYears() : null);
                    detail.setDegree(userDetail.getDegree());
                    detail.setAnnualIncome(userDetail.getAnnualIncome());
                    detail.setOccupation(userDetail.getOccupation());
                    detail.setLocation(userDetail.getPresentAddress());
                    
                    userDetails.add(detail);
                }
            }

            // Add sent requests
            for (InterestRequest request : sentRequests) {
                MailboxUserDetail detail = new MailboxUserDetail();
                UserEntity user = userRepository.findById(request.getInterestReceived()).orElse(null);
                UserDetailEntity userDetail = userDetailRepository.findByUserId(request.getInterestReceived());
                
                if (user != null && userDetail != null) {
                    detail.setUserId(user.getUserId());
                    detail.setFirstName(user.getFirstName());
                    detail.setLastName(user.getLastName());
                    detail.setProfileImage(user.getProfileImage());
                    
                    detail.setAge(user.getDob() != null ? 
                        (int) java.time.Period.between(user.getDob(), java.time.LocalDate.now()).getYears() : null);
                    detail.setDegree(userDetail.getDegree());
                    detail.setAnnualIncome(userDetail.getAnnualIncome());
                    detail.setOccupation(userDetail.getOccupation());
                    detail.setLocation(userDetail.getPresentAddress());
                    
                    userDetails.add(detail);
                }
            }

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Accepted interest requests fetched successfully");
            response.setData(userDetails);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching accepted interest requests: " + e.getMessage());
        }
        return response;
    }

    public ResultResponse getInterestRequestStatus(String senderId, Long receiverId) {
        ResultResponse response = new ResultResponse();
        try {
            String decodedId = new String(Base64.getDecoder().decode(senderId));
            Long senderUserId = Long.parseLong(decodedId);
            System.out.println("Sender ID: " + senderUserId);
            System.out.println("Receiver ID: " + receiverId);
            
            // First check sender-receiver direction
            Optional<ApprovalStatus> status1 = interestRequestRepository.findAcceptStatusBySenderAndReceiver(senderUserId, receiverId);
            System.out.println("Status 11111111111111111111: " + status1);

            HashMap<String, Object> statusMap = new HashMap<>();
            statusMap.put("reqSendedBy", senderUserId);
            
            // If not found, check receiver-sender direction
            if (!status1.isPresent()) {
                Optional<ApprovalStatus> status2 = interestRequestRepository.findAcceptStatusBySenderAndReceiver(receiverId, senderUserId);
                System.out.println("Status 22222222222222222222222: " + status2);
                
                if (!status2.isPresent()) {
                    response.setCode(404);
                    response.setStatus(ResponseStatus.FAILURE);
                    response.setMessage("No interest request found");
                    return response;
                }
               statusMap.put("reqSendedBy", receiverId);
               System.out.println("receiverId============>"+receiverId);

                
                status1 = status2; // Use the status from receiver-sender direction
            }
            System.out.println("senderUserId============>"+senderUserId);
            statusMap.put("status", status1.isPresent() ? status1.get() : null);
            System.out.println("statusMap=================>"+statusMap);
            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Interest request status retrieved");
            response.setData(statusMap);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error checking interest request status: " + e.getMessage());
        }
        return response;
    }
}
