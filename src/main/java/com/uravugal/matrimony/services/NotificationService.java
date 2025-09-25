package com.uravugal.matrimony.services;

import com.uravugal.matrimony.dtos.NotificationResponse;
import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ActiveStatus;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.Notification;
import com.uravugal.matrimony.models.UserEntity;
import com.uravugal.matrimony.repositories.NotificationRepository;
import com.uravugal.matrimony.repositories.UserRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    public ResultResponse getAllNotificationsForUser(String encodedId) {
        ResultResponse response = new ResultResponse();
        try {
            // Decode userId from Base64
            String decodedId = new String(Base64.getDecoder().decode(encodedId));
            Long userId = Long.parseLong(decodedId);

            System.out.println("Decoded ID: " + decodedId);
            // Fetch notifications by receiverId (sorted by createdAt desc)
            List<Notification> notifications = notificationRepository.findByReceiverIdOrderByCreatedAtDesc(userId);

            System.out.println("Notifications: " + notifications);
            // Map each notification to NotificationResponse DTO
            List<NotificationResponse> notificationResponses = notifications.stream().map(notification -> {
                NotificationResponse notifRes = new NotificationResponse();
                notifRes.setNotificationId(notification.getNotificationId());
                notifRes.setNotificationCategory(notification.getNotificationCategory());
                notifRes.setTitle(notification.getTitle()); // Assuming `title` exists — if not, remove this line
                notifRes.setMessage(notification.getMessage());
                notifRes.setTimestamp(notification.getCreatedAt().toString()); // Consider using ISO format
                notifRes.setIsRead(notification.getIsRead());

                // Fetch sender user info
                userRepository.findById(notification.getSenderId()).ifPresent(sender -> {
                    notifRes.setAvatar(sender.getProfileImage());
                    notifRes.setUserName(sender.getFirstName());
                    notifRes.setUserAge(sender.getAge());
                    notifRes.setUserLocation(sender.getLocation());
                });

                notifRes.setSenderId(notification.getSenderId());
                notifRes.setReceiverId(notification.getReceiverId());

                return notifRes;
            }).collect(Collectors.toList());

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Notifications fetched successfully");
            response.setData(notificationResponses);

        } catch (Exception e) {
            e.printStackTrace(); // Add this line
            response.setCode(500);
            response.setMessage("Something went wrong: " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }

        return response;
    }

    public ResultResponse getUnreadNotificationsForUser(Long userId) {
        ResultResponse response = new ResultResponse();
        try {
            List<Notification> notifications = notificationRepository
                    .findByReceiverIdAndIsReadOrderByCreatedAtDesc(userId, ActiveStatus.N);
            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Unread notifications fetched successfully.");
            response.setData(notifications);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching unread notifications: " + e.getMessage());
        }
        return response;
    }

    @Transactional
    public ResultResponse markNotificationsAsRead(Long userId) {
        ResultResponse response = new ResultResponse();
        try {
            int updatedCount = notificationRepository.markNotificationsAsRead(
                    userId,
                    ActiveStatus.Y,
                    ActiveStatus.N);
            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Notifications marked as read successfully. Updated count: " + updatedCount);
            response.setData(updatedCount);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error marking notifications as read: " + e.getMessage());
        }
        return response;
    }

    public ResultResponse getNotificationsByCategory(Long userId, String category) {
        ResultResponse response = new ResultResponse();
        try {
            List<Notification> notifications = notificationRepository
                    .findByReceiverIdAndNotificationCategoryOrderByCreatedAtDesc(userId, category);
            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Notifications by category fetched successfully.");
            response.setData(notifications);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching notifications by category: " + e.getMessage());
        }
        return response;
    }

    @Transactional
    public ResultResponse createNotification(Long userId, Notification notification) {
        ResultResponse response = new ResultResponse();
        try {
            notification.setReceiverId(userId);
            Notification savedNotification = notificationRepository.save(notification);
            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Notification created successfully.");
            response.setData(savedNotification);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error creating notification: " + e.getMessage());
        }
        return response;
    }

    @Transactional
    public ResultResponse deleteNotificationsByUserId(Long userId) {
        ResultResponse response = new ResultResponse();
        try {
            notificationRepository.deleteByReceiverId(userId);
            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("All notifications for user deleted successfully.");
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error deleting notifications: " + e.getMessage());
        }
        return response;
    }

    @Transactional
    public ResultResponse deleteNotificationById(Long notificationId) {
        ResultResponse response = new ResultResponse();
        try {
            notificationRepository.deleteById(notificationId);
            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Notification deleted successfully.");
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error deleting notification: " + e.getMessage());
        }
        return response;
    }

    public ResultResponse getUnreadNotificationCount(Long userId) {
        ResultResponse response = new ResultResponse();
        try {
            Long count = notificationRepository.getUnreadNotificationCount(userId, ActiveStatus.N);
            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Unread notification count fetched successfully.");
            response.setData(count);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching unread notification count: " + e.getMessage());
        }
        return response;
    }

    @Transactional
    public ResultResponse deleteAllNotificationsByReceiverId(String receiverId) {
        ResultResponse response = new ResultResponse();
        try {
            String decodedId = new String(Base64.getDecoder().decode(receiverId));
            Long userId = Long.parseLong(decodedId);

            notificationRepository.deleteAllByReceiverId(userId);
            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("All notifications for receiver deleted successfully.");
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error deleting notifications: " + e.getMessage());
        }
        return response;
    }

    @Transactional
    public ResultResponse markAllAsReadByReceiverId(String receiverId) {
        ResultResponse response = new ResultResponse();
        try {
            String decodedId = new String(Base64.getDecoder().decode(receiverId));
            Long userId = Long.parseLong(decodedId);
            notificationRepository.markAllAsReadByReceiverId(userId);
            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("All notifications marked as read successfully.");
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error marking notifications as read: " + e.getMessage());
        }
        return response;
    }
}
