package com.uravugal.matrimony.controllers;

import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.Notification;
import com.uravugal.matrimony.services.NotificationService;

import java.util.Base64;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    // Get all notifications for a user
    @GetMapping("/getAllNotifications/{encodedId}")
    public ResultResponse getAllNotifications(@PathVariable String encodedId) {
        ResultResponse response = new ResultResponse();
        try {
            response = notificationService.getAllNotificationsForUser(encodedId);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    // Get unread notifications
    @GetMapping("/getUnreadNotifications/{userId}")
    public ResultResponse getUnreadNotifications(@PathVariable Long userId) {
        ResultResponse response = new ResultResponse();
        try {
            response = notificationService.getUnreadNotificationsForUser(userId);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    // Mark notifications as read
    @PostMapping("/markNotificationsAsRead/{userId}")
    public ResultResponse markNotificationsAsRead(@PathVariable Long userId) {
        ResultResponse response = new ResultResponse();
        try {
            response = notificationService.markNotificationsAsRead(userId);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    // Get notifications by category
    @GetMapping("/getNotificationsByCategory/{userId}/{category}")
    public ResultResponse getNotificationsByCategory(@PathVariable Long userId,
            @PathVariable String category) {
        ResultResponse response = new ResultResponse();
        try {
            response = notificationService.getNotificationsByCategory(userId, category);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    // Create a new notification
    @PostMapping("/createNotification/{userId}")
    public ResultResponse createNotification(@PathVariable Long userId, @RequestBody Notification notification) {
        ResultResponse response = new ResultResponse();
        try {
            response = notificationService.createNotification(userId, notification);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    // Delete all notifications by user ID
    @DeleteMapping("/deleteNotificationsByUserId/{userId}")
    public ResultResponse deleteNotificationsByUserId(@PathVariable Long userId) {
        ResultResponse response = new ResultResponse();
        try {
            response = notificationService.deleteNotificationsByUserId(userId);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    // Delete notification by ID
    @DeleteMapping("/deleteNotificationById/{notificationId}")
    public ResultResponse deleteNotificationById(@PathVariable Long notificationId) {
        ResultResponse response = new ResultResponse();
        try {
            response = notificationService.deleteNotificationById(notificationId);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    // Get unread notification count
    @GetMapping("/getUnreadNotificationCount/{encodedId}")
    public ResultResponse getUnreadNotificationCount(@PathVariable String encodedId) {
        ResultResponse response = new ResultResponse();
        try {
            String decodedId = new String(Base64.getDecoder().decode(encodedId));
            Long userId = Long.parseLong(decodedId);
            response = notificationService.getUnreadNotificationCount(userId);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    // Delete all notifications by receiverId
    @DeleteMapping("/deleteAllNotificationsByReceiverId/{receiverId}")
    public ResultResponse deleteAllNotificationsByReceiverId(@PathVariable String receiverId) {
        ResultResponse response = new ResultResponse();
        try {
            response = notificationService.deleteAllNotificationsByReceiverId(receiverId);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    // Mark all as read by receiverId
    @PostMapping("/markAllAsReadByReceiverId/{receiverId}")
    public ResultResponse markAllAsReadByReceiverId(@PathVariable String receiverId) {
        ResultResponse response = new ResultResponse();
        try {
            response = notificationService.markAllAsReadByReceiverId(receiverId);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }
}
