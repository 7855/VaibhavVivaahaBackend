package com.uravugal.matrimony.services;

import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.dtos.UserLikeDetailDTO;
import com.uravugal.matrimony.dtos.UserLikesRequest;
import com.uravugal.matrimony.enums.ActiveStatus;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.enums.SubscriptionStatus;
import com.uravugal.matrimony.models.Features;
import com.uravugal.matrimony.models.Notification;
import com.uravugal.matrimony.models.PlanFeatures;
import com.uravugal.matrimony.models.UserDetailEntity;
import com.uravugal.matrimony.models.UserEntity;
import com.uravugal.matrimony.models.UserLikes;
import com.uravugal.matrimony.models.UserSubscriptions;
import com.uravugal.matrimony.repositories.FeaturesRepository;
import com.uravugal.matrimony.repositories.NotificationRepository;
import com.uravugal.matrimony.repositories.PlanFeaturesRepository;
import com.uravugal.matrimony.repositories.UserDetailRepository;
import com.uravugal.matrimony.repositories.UserLikesRepository;
import com.uravugal.matrimony.repositories.UserRepository;
import com.uravugal.matrimony.repositories.UserSubscriptionsRepository;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserLikesService {

    @Autowired
    private UserLikesRepository userLikesRepository;

    @Autowired
    private UserSubscriptionsRepository userSubscriptionsRepository;

    @Autowired
    private FeaturesRepository featuresRepository;

    @Autowired
    private PlanFeaturesRepository planFeaturesRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserDetailRepository userDetailRepository;

    @Autowired
    private PushNotificationService pushNotificationService;

    public ResultResponse createUserLike(UserLikesRequest userLike) {
        ResultResponse response = new ResultResponse();
        try {
            // Decode the Base64 encoded IDs
            String decodedId = new String(Base64.getDecoder().decode(userLike.getLikedBy()));
            Long likerUserId = Long.parseLong(decodedId);
            Long likedUserId = Long.parseLong(userLike.getLikedTo());
    
            // Check if like already exists
            boolean alreadyLiked = userLikesRepository.existsByLikerAndLikedUser(likerUserId, likedUserId);
    
            if (alreadyLiked) {
                response.setCode(400);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("User has already liked this profile");
                return response;
            }
    
            // Create new like if it doesn't exist
            UserLikes userLikeRequest = new UserLikes();
            userLikeRequest.setLikedBy(likerUserId);
            userLikeRequest.setLikedTo(likedUserId);
    
            UserLikes savedLike = userLikesRepository.save(userLikeRequest);
    
            // ✅ Check NOTIFICATIONS feature for the liked user
            Features notificationsFeature = featuresRepository.findByCode("NOTIFICATIONS");
    
            if (notificationsFeature != null) {
                // Get liked user's active subscription
                List<UserSubscriptions> userSubscriptions = userSubscriptionsRepository
                        .findByUserIdAndStatus(likedUserId, SubscriptionStatus.ACTIVE);
    
                // Only allow notifications for non-free plans
                if (!userSubscriptions.isEmpty() && !userSubscriptions.get(0).getId().equals(1L)) {
    
                    // Check if NOTIFICATIONS feature is part of the liked user's plan
                    PlanFeatures planFeature = planFeaturesRepository.findByFeatureIdAndSubscriptionPlanId(
                            notificationsFeature.getId(),
                            userSubscriptions.get(0).getSubscriptionPlanId());
    
                    if (planFeature != null) {
                        // Fetch both users for names and notification
                        UserEntity receiver = userRepository.findById(likedUserId).orElse(null);
                        UserEntity sender = userRepository.findById(likerUserId).orElse(null);
    
                        if (receiver != null && sender != null) {
                            // Save notification
                            Notification notification = new Notification();
                            notification.setSenderId(likerUserId);
                            notification.setReceiverId(likedUserId);
                            notification.setMessage("liked your profile.");
                            notification.setNotificationCategory("LIKE");
                            notification.setTitle("New Like Received");
                            notificationRepository.save(notification);
    
                            // Send push notification
                            String senderName = sender.getFirstName() + " " + sender.getLastName();
                            pushNotificationService.sendPushNotificationToUser(
                                    likedUserId,
                                    "New Like Received",
                                    senderName + " liked your profile! Check it out now."
                            );
                        }
                    }
                }
            }
    
            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Like created successfully");
            response.setData(savedLike);
    
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error creating like: " + e.getMessage());
        }
        return response;
    }
    

    public ResultResponse checkIfLiked(String likedBy, Long likedTo) {
        ResultResponse response = new ResultResponse();
        try {
            String decodedId = new String(Base64.getDecoder().decode(likedBy));
            Long likerUserId = Long.parseLong(decodedId);
            boolean isLiked = userLikesRepository.existsByLikerAndLikedUser(likerUserId, likedTo);
            System.out.println("isLiked: ============================>" + isLiked);
            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Like check completed");
            response.setData(isLiked);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error checking like status: " + e.getMessage());
        }
        return response;
    }

    public ResultResponse deleteLike(String likedBy, Long likedTo) {
        ResultResponse response = new ResultResponse();
        try {
            String decodedId = new String(Base64.getDecoder().decode(likedBy));
            Long likerUserId = Long.parseLong(decodedId);
            
            // Check if like exists before deleting
            System.out.println("likerUserId: ============================>" + likerUserId);
            System.out.println("likedTo: ============================>" + likedTo);
            boolean exists = userLikesRepository.existsByLikerAndLikedUser(likerUserId, likedTo);
            System.out.println("exists: ============================>" + exists);
            if (!exists) {
                response.setCode(404);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("Like does not exist");
                return response;
            }
            
            // Delete the like
            userLikesRepository.deleteByLikerAndLikedUser(likerUserId, likedTo);
            
            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Like removed successfully");
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error removing like: " + e.getMessage());
        }
        return response;
    }

    // Mirrors ViewedProfileService.getAllViewers's plan-gate + limit pattern exactly (feature
    // code WHO_LIKED, same tiering as WHO_VIEWED: Starter/Classic get a capped count, Silver+
    // unlimited, Free blocked entirely) so "who liked you" behaves the same way as the existing
    // "who viewed you" premium feature.
    public ResultResponse getWhoLikedMe(String encodedUserId) {
        ResultResponse response = new ResultResponse();
        try {
            String decodedUserId = new String(Base64.getDecoder().decode(encodedUserId));
            Long userId = Long.parseLong(decodedUserId);

            List<UserSubscriptions> userSubs = userSubscriptionsRepository
                    .findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE);
            if (userSubs.isEmpty() || userSubs.get(0).getSubscriptionPlanId() == 1) {
                response.setCode(403);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("PLAN_UPGRADE_REQUIRED");
                return response;
            }

            Features whoLikedFeature = featuresRepository.findByCode("WHO_LIKED");
            PlanFeatures planFeature = null;
            if (whoLikedFeature != null) {
                planFeature = planFeaturesRepository.findByFeatureIdAndSubscriptionPlanId(
                        whoLikedFeature.getId(),
                        userSubs.get(0).getSubscriptionPlanId());
            }
            if (planFeature == null) {
                response.setCode(403);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("PLAN_UPGRADE_REQUIRED");
                return response;
            }

            int likerLimit = -1;
            try {
                likerLimit = Integer.parseInt(planFeature.getLimitValue());
            } catch (NumberFormatException ignored) {
                // "enabled"/non-numeric → unlimited
            }

            List<UserLikes> likes = userLikesRepository.findByLikedToAndIsActive(userId, ActiveStatus.Y);

            if (likes == null || likes.isEmpty()) {
                response.setCode(200);
                response.setStatus(ResponseStatus.SUCCESS);
                response.setMessage("No likes found");
                response.setData(new ArrayList<>());
                return response;
            }

            likes = likes.stream()
                    .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                    .collect(Collectors.toList());
            if (likerLimit > 0 && likes.size() > likerLimit) {
                likes = likes.subList(0, likerLimit);
            }

            List<UserLikeDetailDTO> likerDetails = new ArrayList<>();
            for (UserLikes like : likes) {
                UserEntity user = userRepository.findById(like.getLikedBy()).orElse(null);
                if (user == null) continue;
                UserDetailEntity userDetail = userDetailRepository.findByUserId(like.getLikedBy());

                UserLikeDetailDTO detail = new UserLikeDetailDTO();
                detail.setUserId(user.getUserId());
                detail.setMemberId(user.getMemberId());
                detail.setFirstName(user.getFirstName());
                detail.setLastName(user.getLastName());
                detail.setGender(user.getGender() != null ? user.getGender().name() : null);
                detail.setEmail(user.getEmail());
                detail.setMobile(user.getMobile());
                detail.setProfileImage(user.getProfileImage());
                detail.setLikedAt(like.getCreatedAt());

                if (userDetail != null) {
                    if (user.getDob() != null) {
                        detail.setAge((int) java.time.Period.between(user.getDob(), java.time.LocalDate.now()).getYears());
                    }
                    detail.setOccupation(userDetail.getOccupation());
                    detail.setLocation(userDetail.getPresentAddress());
                    detail.setDegree(userDetail.getDegree());
                    detail.setAnnualIncome(userDetail.getAnnualIncome());
                }
                likerDetails.add(detail);
            }

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Liker details fetched successfully");
            response.setData(likerDetails);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching liker details: " + e.getMessage());
        }
        return response;
    }
}