package com.uravugal.matrimony.controllers;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.dtos.SendPushNotificationRequestDTO;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.services.PushNotificationService;

import org.springframework.beans.factory.annotation.Autowired;
import java.util.Map;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/pushNotification")
@CrossOrigin(origins = "*")
public class PushNotificationController {
    @Autowired
    private PushNotificationService pushNotificationService;

    @PostMapping("/sendPushNotificationToUser")
    public ResultResponse sendPushNotificationToUser(@RequestBody SendPushNotificationRequestDTO request) {
        ResultResponse result = new ResultResponse();
        try {
            String userId = request.getUserId();
            String title = request.getTitle();
            String body = request.getBody();
            result = pushNotificationService.sendPushNotificationToUser(Long.parseLong(userId), title, body);
        } catch (Exception e) {
            e.printStackTrace();
            result.setStatus(ResponseStatus.FAILURE);
            result.setCode(500);
            result.setMessage("Error sending push notification: " + e.getMessage());
        }
        return result;
    }

    @PostMapping("/saveDeviceInfo")
    public ResultResponse saveDeviceInfo(@RequestBody Map<String, String> request) {
        ResultResponse response = new ResultResponse();
        try {
            response = pushNotificationService.saveUserDeviceInfo(request);
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(ResponseStatus.FAILURE);
            response.setCode(500);
            response.setMessage("Error saving device info: " + e.getMessage());
        }
        return response;
    }
    
    @PostMapping("/updateToken")
    public ResultResponse updateFcmToken(@RequestBody Map<String, String> request) {
        ResultResponse response = new ResultResponse();
        try {
            response = pushNotificationService.updateFcmTokenForUser(request);
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(ResponseStatus.FAILURE);
            response.setCode(500);
            response.setMessage("Error updating FCM token: " + e.getMessage());
        }
        return response;
    }
    
    @DeleteMapping("/deleteDevice")
    public ResultResponse deleteDeviceInfo(@RequestBody Map<String, String> request) {
        ResultResponse response = new ResultResponse();
        try {
            response = pushNotificationService.deleteUserDeviceInfo(request);
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(ResponseStatus.FAILURE);
            response.setCode(500);
            response.setMessage("Error deleting device info: " + e.getMessage());
        }
        return response;
    }

}
