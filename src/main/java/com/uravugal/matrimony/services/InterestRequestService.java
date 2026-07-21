package com.uravugal.matrimony.services;

import com.uravugal.matrimony.dtos.MailboxUserDetail;
import com.uravugal.matrimony.dtos.PaginatedResultResponse;
import com.uravugal.matrimony.dtos.PaginationData;
import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.models.ChatEntity;
import com.uravugal.matrimony.models.Conversation;
import com.uravugal.matrimony.models.Features;
import com.uravugal.matrimony.models.InterestRequest;
import com.uravugal.matrimony.models.Notification;
import com.uravugal.matrimony.models.UserEntity;
import com.uravugal.matrimony.models.UserSubscriptions;
import com.uravugal.matrimony.models.UserDetailEntity;
import com.uravugal.matrimony.enums.ApprovalStatus;
import com.uravugal.matrimony.enums.ChatStatus;
import com.uravugal.matrimony.enums.FeatureType;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.repositories.ChatRepository;
import com.uravugal.matrimony.repositories.ConversationRepository;
import com.uravugal.matrimony.repositories.FeaturesRepository;
import com.uravugal.matrimony.repositories.InterestRequestRepository;
import com.uravugal.matrimony.repositories.NotificationRepository;
import com.uravugal.matrimony.repositories.UserDetailRepository;
import com.uravugal.matrimony.repositories.UserRepository;
import com.uravugal.matrimony.repositories.UserSubscriptionsRepository;

import jakarta.transaction.Transactional;

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

    @Autowired
    private UserSubscriptionsRepository userSubscriptionsRepository;

    @Autowired
    private UserFeatureUsageService userFeatureUsageService;

    @Autowired
    private FeaturesRepository featuresRepository;

    @Autowired
    private com.uravugal.matrimony.repositories.BlockedUserRepository blockedUserRepository;


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

            // Block filter: skip requests from users blocked by or blocking the viewer
            java.util.Set<Long> blockedIds = new java.util.HashSet<>(
                    blockedUserRepository.findBlockAdjacentUserIds(userId));

            // Fetch user details for each request
            List<MailboxUserDetail> userDetails = requests.getContent().stream()
                .filter(request -> !blockedIds.contains(request.getInterestSend()))
                .map(request -> {
                    MailboxUserDetail detail = new MailboxUserDetail();
                    UserEntity user = userRepository.findById(request.getInterestSend()).orElse(null);
                    UserDetailEntity userDetail = userDetailRepository.findByUserId(request.getInterestSend());
                    
                    if (user != null) {
                        detail.setUserId(user.getUserId());
                        detail.setFirstName(user.getFirstName());
                        detail.setLastName(user.getLastName());
                        detail.setProfileImage(user.getProfileImage());
                        detail.setIdVerified(Boolean.TRUE.equals(user.getIdVerified()));
                        detail.setEducationVerified(Boolean.TRUE.equals(user.getEducationVerified()));
                        detail.setIncomeVerified(Boolean.TRUE.equals(user.getIncomeVerified()));
                        
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
                        detail.setIdVerified(Boolean.TRUE.equals(user.getIdVerified()));
                        detail.setEducationVerified(Boolean.TRUE.equals(user.getEducationVerified()));
                        detail.setIncomeVerified(Boolean.TRUE.equals(user.getIncomeVerified()));
                        
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
                        detail.setIdVerified(Boolean.TRUE.equals(user.getIdVerified()));
                        detail.setEducationVerified(Boolean.TRUE.equals(user.getEducationVerified()));
                        detail.setIncomeVerified(Boolean.TRUE.equals(user.getIncomeVerified()));
                        
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
            Page<InterestRequest> requests = interestRequestRepository.findByInterestSend(
                userId,
                PageRequest.of(page != null ? page : 0, size != null ? size : 10)
            );
            
            if (requests.isEmpty()) {
                resp.setCode(404);
                resp.setMessage("No interest requests sent");
                resp.setStatus(ResponseStatus.FAILURE);
                return resp;
            }

            // Block filter
            java.util.Set<Long> blockedIds = new java.util.HashSet<>(
                    blockedUserRepository.findBlockAdjacentUserIds(userId));

            // Fetch user details for each request
            List<MailboxUserDetail> userDetails = requests.getContent().stream()
                .filter(request -> !blockedIds.contains(request.getInterestReceived()))
                .map(request -> {
                    MailboxUserDetail detail = new MailboxUserDetail();
                    UserEntity user = userRepository.findById(request.getInterestReceived()).orElse(null);
                    UserDetailEntity userDetail = userDetailRepository.findByUserId(request.getInterestReceived());

                    if (user != null) {
                        detail.setUserId(user.getUserId());
                        detail.setFirstName(user.getFirstName());
                        detail.setLastName(user.getLastName());
                        detail.setProfileImage(user.getProfileImage());
                        detail.setIdVerified(Boolean.TRUE.equals(user.getIdVerified()));
                        detail.setEducationVerified(Boolean.TRUE.equals(user.getEducationVerified()));
                        detail.setIncomeVerified(Boolean.TRUE.equals(user.getIncomeVerified()));

                        if (userDetail != null) {
                            detail.setAge(user.getDob() != null ?
                                (int) java.time.Period.between(user.getDob(), java.time.LocalDate.now()).getYears() : null);
                            detail.setDegree(userDetail.getDegree());
                            detail.setAnnualIncome(userDetail.getAnnualIncome());
                            detail.setOccupation(userDetail.getOccupation());
                            detail.setLocation(userDetail.getPresentAddress());
                        }
                        detail.setInterestId(request.getId());
                        detail.setAcceptStatus(request.getAcceptStatus() != null ? request.getAcceptStatus().name() : "PENDING");
                    }
                    return detail;
                })
                .filter(detail -> detail.getUserId() != null)
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
                        detail.setIdVerified(Boolean.TRUE.equals(user.getIdVerified()));
                        detail.setEducationVerified(Boolean.TRUE.equals(user.getEducationVerified()));
                        detail.setIncomeVerified(Boolean.TRUE.equals(user.getIncomeVerified()));
                        
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
                // Find conversation (normalized: smaller userId = userOne)
                Long minId = Math.min(request.getInterestSend(), request.getInterestReceived());
                Long maxId = Math.max(request.getInterestSend(), request.getInterestReceived());
                Conversation convo = conversationRepository.findByUserOneAndUserTwo(minId, maxId);
                // Fallback: try original order if normalized lookup fails (for old data)
                if (convo == null) {
                    convo = conversationRepository.findByUserOneAndUserTwo(request.getInterestSend(), request.getInterestReceived());
                }
                if(convo != null) {
                    convo.setStatus(ChatStatus.ACCEPTED);
                    conversationRepository.save(convo);

                    ChatEntity chat = new ChatEntity();
                    chat.setConversationId(convo.getId());
                    chat.setSenderId(request.getInterestReceived());
                    chat.setMessage("Interest request approved. I have liked your profile too. Let's discuss further details.");
                    chatRepository.save(chat);
                }

                // Push notification to the original sender that their interest was ACCEPTED
                UserEntity acceptedBy = userRepository.findById(request.getInterestReceived()).orElse(null);
                String acceptorName = acceptedBy != null
                    ? acceptedBy.getFirstName() + " " + acceptedBy.getLastName()
                    : "Someone";
                pushNotificationService.sendPushNotificationToUserDirect(
                    request.getInterestSend(),
                    "Interest Accepted! 🎉",
                    acceptorName + " accepted your interest request. Start chatting now!"
                );

                // Save in-app notification
                Notification notification = new Notification();
                notification.setSenderId(request.getInterestReceived());
                notification.setReceiverId(request.getInterestSend());
                notification.setTitle("Interest Accepted");
                notification.setMessage("accepted your interest request");
                notification.setNotificationCategory("INTEREST_ACCEPTED");
                notificationRepository.save(notification);
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

            Long sendId = request.getInterestSend();
            Long recvId = request.getInterestReceived();

            interestRequestRepository.delete(request);

            // Cancelling a sent interest used to leave its auto-created conversation behind
            // (visible in the chat list with only the "I have liked your profile" system
            // message, even though the request itself no longer shows as pending). Clean it up —
            // but only when no real conversation ever happened on top of it; if either side sent
            // an actual message, leave the conversation alone.
            Long minId = Math.min(sendId, recvId);
            Long maxId = Math.max(sendId, recvId);
            Conversation convo = conversationRepository.findByUserOneAndUserTwo(minId, maxId);
            if (convo != null) {
                Long realMessageCount = chatRepository.countRealMessagesByConversationId(convo.getId());
                if (realMessageCount == null || realMessageCount == 0) {
                    chatRepository.deleteAll(chatRepository.findAllByConversationId(convo.getId()));
                    conversationRepository.delete(convo);
                }
            }

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
            Long sendId = request.getInterestSend();
            Long recvId = request.getInterestReceived();

            // A DB-level unique constraint on (interestSend, interestReceived) means we can
            // never insert a second row for this pair — a prior REJECTED row must be reused.
            Optional<InterestRequest> existingOpt = interestRequestRepository
                    .findByInterestSendAndInterestReceived(sendId, recvId);

            InterestRequest savedRequest;
            boolean isResend = false;

            if (existingOpt.isPresent()) {
                InterestRequest existing = existingOpt.get();
                if (existing.getAcceptStatus() == ApprovalStatus.PENDING
                        || existing.getAcceptStatus() == ApprovalStatus.APPROVED) {
                    resp.setCode(400);
                    resp.setMessage("Interest request already exists");
                    resp.setStatus(ResponseStatus.FAILURE);
                    return resp;
                }
                // Previously REJECTED — allow sending again by resetting the same row to PENDING
                existing.setAcceptStatus(ApprovalStatus.PENDING);
                savedRequest = interestRequestRepository.save(existing);
                isResend = true;
            } else {
                request.setAcceptStatus(ApprovalStatus.PENDING);
                savedRequest = interestRequestRepository.save(request);
            }

            pushNotificationService.sendPushNotificationToUserDirect(recvId, "You've Received an Interest", "Someone has expressed interest in your profile. Check now to see who it is!");
            // Create notification for the receiver
            Notification notification = new Notification();
            notification.setSenderId(sendId);
            notification.setReceiverId(recvId);
            notification.setMessage("Expressed interest in your profile");
            notification.setNotificationCategory("INTEREST");
            notification.setTitle("Interest Expressed");
            notificationRepository.save(notification);

            // Check for an existing conversation directly rather than trusting isResend —
            // a conversation can outlive its InterestRequest row (e.g. the sender canceled/
            // deleted the request via "Sent By You", which only deletes the InterestRequest,
            // not the Conversation). A unique constraint on (userOne, userTwo) means a second
            // INSERT for the same pair throws — so always check reality first.
            Conversation existingConvo = conversationRepository.findByUserOneAndUserTwo(
                    Math.min(sendId, recvId), Math.max(sendId, recvId));

            if (existingConvo == null) {
                // Create conversation (normalize: smaller userId = userOne for bidirectional uniqueness)
                Conversation conversation = new Conversation();
                conversation.setUserOne(Math.min(sendId, recvId));
                conversation.setUserTwo(Math.max(sendId, recvId));
                conversation.setStatus(ChatStatus.PENDING);
                conversation.setInitiatedBy(sendId);
                Conversation savedConversation = conversationRepository.save(conversation);

                ChatEntity chat = new ChatEntity();
                chat.setConversationId(savedConversation.getId());
                chat.setSenderId(sendId);
                chat.setMessage(
                        "I have liked your profile. For further information discussion, please approve my request.");
                chatRepository.save(chat);
            } else {
                // Conversation already exists from an earlier send — reuse it instead of
                // creating a duplicate.
                existingConvo.setStatus(ChatStatus.PENDING);
                conversationRepository.save(existingConvo);

                ChatEntity chat = new ChatEntity();
                chat.setConversationId(existingConvo.getId());
                chat.setSenderId(sendId);
                chat.setMessage(
                        "I have liked your profile again. For further information discussion, please approve my request.");
                chatRepository.save(chat);
            }

            resp.setCode(201);
            resp.setMessage(isResend ? "Interest request re-sent successfully" : "Interest request created successfully");
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
                if (user == null) continue;
                UserDetailEntity userDetail = userDetailRepository.findByUserId(request.getInterestSend());

                detail.setUserId(user.getUserId());
                detail.setFirstName(user.getFirstName());
                detail.setLastName(user.getLastName());
                detail.setProfileImage(user.getProfileImage());
                detail.setIdVerified(Boolean.TRUE.equals(user.getIdVerified()));
                detail.setEducationVerified(Boolean.TRUE.equals(user.getEducationVerified()));
                detail.setIncomeVerified(Boolean.TRUE.equals(user.getIncomeVerified()));
                detail.setAge(user.getDob() != null ?
                    (int) java.time.Period.between(user.getDob(), java.time.LocalDate.now()).getYears() : null);
                if (userDetail != null) {
                    detail.setDegree(userDetail.getDegree());
                    detail.setAnnualIncome(userDetail.getAnnualIncome());
                    detail.setOccupation(userDetail.getOccupation());
                    detail.setLocation(userDetail.getPresentAddress());
                }
                userDetails.add(detail);
            }

            // Add sent requests
            for (InterestRequest request : sentRequests) {
                MailboxUserDetail detail = new MailboxUserDetail();
                UserEntity user = userRepository.findById(request.getInterestReceived()).orElse(null);
                if (user == null) continue;
                UserDetailEntity userDetail = userDetailRepository.findByUserId(request.getInterestReceived());

                detail.setUserId(user.getUserId());
                detail.setFirstName(user.getFirstName());
                detail.setLastName(user.getLastName());
                detail.setProfileImage(user.getProfileImage());
                detail.setIdVerified(Boolean.TRUE.equals(user.getIdVerified()));
                detail.setEducationVerified(Boolean.TRUE.equals(user.getEducationVerified()));
                detail.setIncomeVerified(Boolean.TRUE.equals(user.getIncomeVerified()));
                detail.setAge(user.getDob() != null ?
                    (int) java.time.Period.between(user.getDob(), java.time.LocalDate.now()).getYears() : null);
                if (userDetail != null) {
                    detail.setDegree(userDetail.getDegree());
                    detail.setAnnualIncome(userDetail.getAnnualIncome());
                    detail.setOccupation(userDetail.getOccupation());
                    detail.setLocation(userDetail.getPresentAddress());
                }
                userDetails.add(detail);
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

    @Transactional
    public void sendInterestWithLimit(Long senderId, Long receiverId) {

        // 0️⃣ Block gate
        com.uravugal.matrimony.models.BlockedUser block = blockedUserRepository
                .findByUsersEitherDirection(senderId, receiverId);
        if (block != null) {
            throw new RuntimeException("USER_BLOCKED");
        }

        // 1️⃣ Check duplicate request
        if (interestRequestRepository
                .existsByInterestSendAndInterestReceived(senderId, receiverId)) {
            throw new RuntimeException("INTEREST_ALREADY_SENT");
        }

        // 2️⃣ Fetch active subscription
        UserSubscriptions subscription =
                userSubscriptionsRepository.findTopByUserIdOrderByCreatedAtDesc(senderId);

        Features feature = featuresRepository.findByCode("REQ_LIMITED");

    

        if (subscription == null) {
            throw new RuntimeException("NO_ACTIVE_SUBSCRIPTION");
        }
        System.out.println("subscription=============>"+subscription);

        // 3️⃣ Validate & increment usage (FEATURE_ID = SEND_INTEREST)
        userFeatureUsageService.validateAndIncrementUsage(
                senderId,
                subscription.getId(),
                feature.getId()        );
        System.out.println("After used feature");

        // 4️⃣ Create interest request
        InterestRequest request = new InterestRequest();
        request.setInterestSend(senderId);
        request.setInterestReceived(receiverId);
        request.setAcceptStatus(ApprovalStatus.PENDING);
        interestRequestRepository.save(request);

        // 5️⃣ Push notification
        pushNotificationService.sendPushNotificationToUserDirect(
                receiverId,
                "You’ve Received an Interest",
                "Someone has expressed interest in your profile"
        );

        // 6️⃣ Save notification
        Notification notification = new Notification();
        notification.setSenderId(senderId);
        notification.setReceiverId(receiverId);
        notification.setTitle("Interest Expressed");
        notification.setMessage("Expressed interest in your profile");
        notification.setNotificationCategory("INTEREST");
        notificationRepository.save(notification);

        // 7️⃣ Create conversation (normalize: smaller userId = userOne)
        Conversation conversation = new Conversation();
        conversation.setUserOne(Math.min(senderId, receiverId));
        conversation.setUserTwo(Math.max(senderId, receiverId));
        conversation.setStatus(ChatStatus.PENDING);
        conversation.setInitiatedBy(senderId);
        Conversation savedConversation = conversationRepository.save(conversation);

        // 8️⃣ Initial chat message
        ChatEntity chat = new ChatEntity();
        chat.setConversationId(savedConversation.getId());
        chat.setSenderId(senderId);
        chat.setMessage("I have liked your profile. Please approve my request.");
        chatRepository.save(chat);
    }
}
