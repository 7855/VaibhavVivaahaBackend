package com.uravugal.matrimony.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.UserDeviceInformation;
import com.uravugal.matrimony.repositories.PushNotificationRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
// Using fully qualified name for MediaType to avoid ambiguity
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;


@Service
public class PushNotificationService {

    @Autowired
    private PushNotificationRepository pushNotificationRepository;

    public ResultResponse updateFcmTokenForUser(Map<String, String> request) {
        ResultResponse response = new ResultResponse();

        try {
            String userIdStr = request.get("userId");
            String deviceId = request.get("deviceId");
            String newToken = request.get("fcmToken");

            if (userIdStr == null || newToken == null) {
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("Both userId and fcmToken are required.");
                return response;
            }

            Long userId = Long.parseLong(userIdStr);

            Optional<UserDeviceInformation> optionalDevice = pushNotificationRepository.findByUserIdAndDeviceId(userId, deviceId);
            if (optionalDevice.isPresent()) {
                UserDeviceInformation deviceInfo = optionalDevice.get();
                deviceInfo.setFcmToken(newToken);
                deviceInfo.setUpdatedAt(new Date());
                pushNotificationRepository.save(deviceInfo);

                response.setStatus(ResponseStatus.SUCCESS);
                response.setMessage("Token updated successfully.");
                response.setCode(200);
            } else {
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("User device info not found.");
                response.setCode(404);
            }
        } catch (NumberFormatException e) {
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Invalid userId format. Please provide a valid number.");
            response.setCode(400);
        } catch (Exception e) {
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error updating token: " + e.getMessage());
            response.setCode(500);
        }

        return response;
    }

    public ResultResponse saveUserDeviceInfo(Map<String, String> request) {
        ResultResponse response = new ResultResponse();

        try {
            String token = request.get("fcmToken");
            String userIdStr = request.get("userId");
            String deviceId = request.get("deviceId");
            String deviceType = request.get("deviceType");
            String deviceModel = request.get("deviceModel");
            String appVersion = request.get("appVersion");
            String osVersion = request.get("osVersion");

            if (token == null || userIdStr == null || deviceId == null) {
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("Missing required fields: fcmToken, userId, deviceId.");
                return response;
            }

            Long userId = Long.parseLong(userIdStr);

            // First check if device exists for this userId and deviceId
            Optional<UserDeviceInformation> existingDevice = pushNotificationRepository.findByUserIdAndDeviceId(userId, deviceId);
            UserDeviceInformation deviceInfo;

            if (existingDevice.isPresent()) {
                // Update existing device info
                deviceInfo = existingDevice.get();
            } else {
                // Check if FCM token exists for different device
                Optional<UserDeviceInformation> existingTokenDevice = pushNotificationRepository.findByFcmToken(token);
                if (existingTokenDevice.isPresent()) {
                    deviceInfo = existingTokenDevice.get();
                } else {
                    deviceInfo = new UserDeviceInformation();
                }
                deviceInfo.setUserId(userId);
                deviceInfo.setDeviceId(deviceId);
            }

            // Update all device details
            deviceInfo.setFcmToken(token);
            deviceInfo.setDeviceType(deviceType);
            deviceInfo.setDeviceModel(deviceModel);
            deviceInfo.setAppVersion(appVersion);
            deviceInfo.setOsVersion(osVersion);
            deviceInfo.setUpdatedAt(new Date());
            
            pushNotificationRepository.save(deviceInfo);

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Device info " + (existingDevice.isPresent() ? "updated" : "saved") + " successfully.");
        } catch (NumberFormatException e) {
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Invalid userId format. Please provide a valid number.");
        } catch (Exception e) {
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error processing device info: " + e.getMessage());
        }

        return response;
    }

    // @Autowired
    // private FirebaseMessaging firebaseMessaging;

    // Method to check if token is an Expo push token
    private boolean isExpoPushToken(String token) {
        return token != null && token.startsWith("ExponentPushToken");
    }

    @Value("${expo.push.api.url:https://exp.host/--/api/v2/push/send}")
    private String expoApiUrl;

    // Method to send push notification via Expo
    private void sendExpoPushNotification(String token, String title, String body, List<String> successfulTokens, List<String> failedTokens) {
        try {
            // Create JSON request body
            JSONObject message = new JSONObject();
            JSONArray to = new JSONArray();
            to.put(token);
            message.put("to", to);
            message.put("title", title);
            message.put("body", body);
            message.put("sound", "default");
            message.put("channelId", "default");

            // Create headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            headers.setAccept(Collections.singletonList(org.springframework.http.MediaType.APPLICATION_JSON));

            // Create HTTP entity
            HttpEntity<String> requestEntity = new HttpEntity<>(message.toString(), headers);

            // Log the request
            System.out.println("Sending Expo push notification to: "+ token);
            System.out.println("Request payload: "+ message.toString());
            
            try {
                // Send POST request to Expo push notification service
                RestTemplate restTemplate = new RestTemplate();
                ResponseEntity<String> response = restTemplate.exchange(
                    expoApiUrl,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
                );

                // Check response status
                if (response.getStatusCode() == HttpStatus.OK) {
                    System.out.println("Successfully sent push notification to: "+ token);
                    System.out.println("Response: "+ response.getBody());
                    successfulTokens.add(token);
                } else {
                    String errorMsg = String.format("Failed to send Expo push notification. Status: %s, Response: %s", 
                        response.getStatusCode(), response.getBody());
                    System.out.println(errorMsg);
                    failedTokens.add(token);
                }
            } catch (Exception e) {
                String errorMsg = String.format("Exception while sending push notification to %s: %s", token, e.getMessage());
                System.out.println(errorMsg);
                failedTokens.add(token);
            }
        } catch (Exception e) {
            System.out.println("Error sending Expo push notification: "+ e.getMessage());
            failedTokens.add(token);
        }
    }

    // Method to send FCM push notification
    private void sendFcmPushNotification(String token, String title, String body, List<String> successfulTokens, List<String> failedTokens) {
        try {
            Message message = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                    .setTitle(title.trim())
                    .setBody(body.trim())
                    .build())
                .build();

            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<String> future = executor
                .submit(() -> FirebaseMessaging.getInstance().send(message));
            String responseId = future.get(5, TimeUnit.SECONDS); // Timeout after 5 seconds
            executor.shutdown();
            successfulTokens.add(token);
        } catch (TimeoutException e) {
            System.out.println("Timeout sending FCM to token: "+ token);
            failedTokens.add(token);
        } catch (Exception e) {
            System.out.println("Error sending FCM to token: "+ token+" "+ e.getMessage());
            failedTokens.add(token);
        }
    }

    public ResultResponse sendPushNotificationToUser(Long userId, String title, String body) {
        ResultResponse response = new ResultResponse();
        try {
            // Validate input parameters
            if (userId == null || userId <= 0) {
                response.setStatus(ResponseStatus.FAILURE);
                response.setCode(400);
                response.setMessage("Invalid userId provided");
                return response;
            }

            if (title == null || title.trim().isEmpty() || body == null || body.trim().isEmpty()) {
                response.setStatus(ResponseStatus.FAILURE);
                response.setCode(400);
                response.setMessage("Title and body cannot be empty");
                return response;
            }

            List<UserDeviceInformation> deviceInfoList = pushNotificationRepository.findAllByUserId(userId);
            if (!deviceInfoList.isEmpty()) {
                List<String> successfulTokens = new ArrayList<>();
                List<String> failedTokens = new ArrayList<>();

                for (UserDeviceInformation deviceInfo : deviceInfoList) {
                    String pushToken = deviceInfo.getFcmToken();
                    if (pushToken != null) {
                        if (isExpoPushToken(pushToken)) {
                            sendExpoPushNotification(pushToken, title.trim(), body.trim(), successfulTokens, failedTokens);
                        } else {
                            sendFcmPushNotification(pushToken, title, body, successfulTokens, failedTokens);
                        }
                    }
                }

                // Prepare response based on results
                Map<String, Object> data = new HashMap<>();
                data.put("totalDevices", deviceInfoList.size());
                data.put("successfulDeliveries", successfulTokens.size());
                data.put("failedDeliveries", failedTokens.size());

                if (!successfulTokens.isEmpty()) {
                    response.setStatus(ResponseStatus.SUCCESS);
                    response.setCode(200);
                    response.setMessage("Push notification sent to " + successfulTokens.size() + " devices");
                    response.setData(data);
                } else {
                    String errorMsg = String.format("Failed to send notifications to any device. Total devices: %d, Failed: %d", 
                        deviceInfoList.size(), failedTokens.size());
                    System.out.println(errorMsg);
                    response.setStatus(ResponseStatus.FAILURE);
                    response.setCode(502);
                    response.setMessage(errorMsg);
                    response.setData(data);
                }
            } else {
                String errorMsg = String.format("No devices found for user ID: %d", userId);
                System.out.println(errorMsg);
                response.setStatus(ResponseStatus.FAILURE);
                response.setCode(404);
                response.setMessage(errorMsg);
            }
        } catch (Exception e) {
            String errorMsg = String.format("Error sending push notification to user ID %d: %s", userId, e.getMessage());
            System.out.println(errorMsg);
            response.setStatus(ResponseStatus.FAILURE);
            response.setCode(500);
            response.setMessage("Internal server error: " + e.getMessage());
        }
        return response;
    }

    public ResultResponse deleteUserDeviceInfo(Map<String, String> request) {
        ResultResponse response = new ResultResponse();
        try {
            String userIdStr = request.get("userId");
            String deviceId = request.get("deviceId");
            
            if (userIdStr == null || deviceId == null) {
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("Both userId and deviceId are required.");
                response.setCode(400);
                return response;
            }
            
            Long userId = Long.parseLong(userIdStr);
            
            // Check if the device exists
            Optional<UserDeviceInformation> existingDevice = pushNotificationRepository.findByUserIdAndDeviceId(userId, deviceId);
            if (!existingDevice.isPresent()) {
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("Device not found for the given user.");
                response.setCode(404);
                return response;
            }
            
            // Delete the device
            pushNotificationRepository.deleteByUserIdAndDeviceId(userId, deviceId);
            
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Device information deleted successfully.");
            response.setCode(200);
        } catch (NumberFormatException e) {
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Invalid userId format. Please provide a valid number.");
            response.setCode(400);
        } catch (Exception e) {
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error deleting device info: " + e.getMessage());
            response.setCode(500);
        }
        return response;
    }
    
}
