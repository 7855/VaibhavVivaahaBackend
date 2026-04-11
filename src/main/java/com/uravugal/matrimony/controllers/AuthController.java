package com.uravugal.matrimony.controllers;

import com.uravugal.matrimony.config.RateLimiter;
import com.uravugal.matrimony.dtos.AuthOtpRequest;
import com.uravugal.matrimony.dtos.AuthVerifyOtpRequest;
import com.uravugal.matrimony.dtos.ForgotPasswordRequest;
import com.uravugal.matrimony.dtos.RefreshTokenRequest;
import com.uravugal.matrimony.dtos.ResetPasswordRequest;
import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.services.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private RateLimiter rateLimiter;

    @PostMapping("/send-otp")
    public ResultResponse sendOtp(@Valid @RequestBody AuthOtpRequest request) {
        ResultResponse resp = new ResultResponse();
        try {
            // Rate limit: max 5 OTP requests per email per 5 minutes
            String key = "auth-otp:" + request.getEmail();
            if (!rateLimiter.isAllowed(key, 5, 5 * 60 * 1000)) {
                resp.setCode(429);
                resp.setMessage("Too many OTP requests. Please try again after 5 minutes.");
                resp.setStatus(ResponseStatus.FAILURE);
                return resp;
            }
            resp = authService.sendOtp(request.getEmail(), request.getPurpose(), request.getFirstName());
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @PostMapping("/verify-otp")
    public ResultResponse verifyOtp(@Valid @RequestBody AuthVerifyOtpRequest request) {
        ResultResponse resp = new ResultResponse();
        try {
            resp = authService.verifyOtp(request.getEmail(), request.getOtp(), request.getPurpose());
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @PostMapping("/forgot-password")
    public ResultResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        ResultResponse resp = new ResultResponse();
        try {
            String key = "auth-forgot:" + request.getEmail();
            if (!rateLimiter.isAllowed(key, 5, 5 * 60 * 1000)) {
                resp.setCode(429);
                resp.setMessage("Too many requests. Please try again after 5 minutes.");
                resp.setStatus(ResponseStatus.FAILURE);
                return resp;
            }
            resp = authService.sendOtp(request.getEmail(), "reset", null);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @PostMapping("/refresh")
    public ResultResponse refresh(@RequestBody RefreshTokenRequest request) {
        ResultResponse resp = new ResultResponse();
        try {
            resp = authService.refreshAccessToken(request.getRefreshToken());
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @PostMapping("/logout")
    public ResultResponse logout(@RequestBody RefreshTokenRequest request) {
        ResultResponse resp = new ResultResponse();
        try {
            resp = authService.logout(request.getRefreshToken());
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @PostMapping("/reset-password")
    public ResultResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        ResultResponse resp = new ResultResponse();
        try {
            resp = authService.resetPassword(request.getResetToken(), request.getNewPassword());
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }
}