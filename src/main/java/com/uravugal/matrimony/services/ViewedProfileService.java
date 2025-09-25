package com.uravugal.matrimony.services;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.dtos.ViewedProfileDetailDTO;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.Notification;
import com.uravugal.matrimony.models.UserDetailEntity;
import com.uravugal.matrimony.models.UserEntity;
import com.uravugal.matrimony.models.ViewedProfile;
import com.uravugal.matrimony.repositories.NotificationRepository;
import com.uravugal.matrimony.repositories.UserDetailRepository;
import com.uravugal.matrimony.repositories.UserRepository;
import com.uravugal.matrimony.repositories.ViewedProfileRepository;

@Service
public class ViewedProfileService {
    
    @Autowired
    private ViewedProfileRepository viewedProfileRepository;
    
    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserDetailRepository userDetailRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PushNotificationService pushNotificationService;

    @Transactional
    public ResultResponse handleProfileView(String viewerId, Long viewedUserId) {
        ResultResponse response = new ResultResponse();
        try {
            String decodedViewerId = new String(Base64.getDecoder().decode(viewerId));
            Long decodedViewerIdLong = Long.parseLong(decodedViewerId);
            // Check if user has already viewed this profile
            if (!viewedProfileRepository.existsByUserIdValueAndViewedBy(viewedUserId, decodedViewerIdLong)) {
                // Save profile view
                ViewedProfile viewedProfile = new ViewedProfile();
                viewedProfile.setUserIdValue(viewedUserId);
                viewedProfile.setViewedBy(decodedViewerIdLong);
                viewedProfileRepository.save(viewedProfile);

                // Create Notification
                Notification notification = new Notification();
                notification.setSenderId(decodedViewerIdLong);
                notification.setReceiverId(viewedUserId);
                notification.setMessage("viewed your profile.");
                notification.setNotificationCategory("PROFILE_VIEW");
                notification.setTitle("Profile Viewed");
                notificationRepository.save(notification);
                
                pushNotificationService.sendPushNotificationToUser(viewedUserId, "Profile Viewed", "Someone has viewed your profile. Check now to see who it is!");
                
                UserEntity user = userRepository.findById(viewedUserId).orElse(null);
                if (user != null) {
                    user.setViewCount(user.getViewCount() + 1);
                    userRepository.save(user);
                }
                response.setCode(200);
                response.setStatus(ResponseStatus.SUCCESS);
                response.setMessage("Profile view recorded successfully");
            } else {
                response.setCode(200);
                response.setStatus(ResponseStatus.SUCCESS);
                response.setMessage("Profile view already recorded");
            }
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error recording profile view: " + e.getMessage());
        }
        return response;
    }

    public ResultResponse getAllViewers(String encodedUserId) {
        ResultResponse response = new ResultResponse();
        try {
            String decodedUserId = new String(Base64.getDecoder().decode(encodedUserId));
            Long userId = Long.parseLong(decodedUserId);
            System.out.println("userId: loged" + userId);
            
            List<ViewedProfile> viewers = viewedProfileRepository.findByUserIdValue(userId);
            
            if (viewers == null || viewers.isEmpty()) {
                response.setCode(200);
                response.setStatus(ResponseStatus.SUCCESS);
                response.setMessage("No viewers found");
                response.setData(new ArrayList<>());
                return response;
            }
            
            // Get user details for each viewer
            List<ViewedProfileDetailDTO> viewerDetails = new ArrayList<>();
            for (ViewedProfile viewedProfile : viewers) {
                ViewedProfileDetailDTO detail = new ViewedProfileDetailDTO();
                UserEntity user = userRepository.findById(viewedProfile.getViewedBy()).orElse(null);
                UserDetailEntity userDetail = userDetailRepository.findByUserId(viewedProfile.getViewedBy());
                
                if (user != null) {
                    detail.setUserId(user.getUserId());
                    detail.setMemberId(user.getMemberId());
                    detail.setFirstName(user.getFirstName());
                    detail.setLastName(user.getLastName());
                    detail.setGender(user.getGender().name());
                    detail.setEmail(user.getEmail());
                    detail.setMobile(user.getMobile());
                    detail.setProfileImage(user.getProfileImage());
                    detail.setViewedAt(viewedProfile.getCreatedAt());
                    
                    if (userDetail != null) {
                        // Calculate age from DOB
                        if (user.getDob() != null) {
                            detail.setAge((int) java.time.Period.between(user.getDob(), java.time.LocalDate.now()).getYears());
                        }
                        
                        // Map user detail fields
                        detail.setOccupation(userDetail.getOccupation());
                        detail.setLocation(userDetail.getPresentAddress());
                        detail.setDegree(userDetail.getDegree());
                        detail.setAnnualIncome(userDetail.getAnnualIncome());
                    }
                    viewerDetails.add(detail);
                }
            }

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Viewer details fetched successfully");
            response.setData(viewerDetails);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching viewer details: " + e.getMessage());
        }
        return response;
    }
}
