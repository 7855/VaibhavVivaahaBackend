package com.uravugal.matrimony.services;

import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.UserEntity;
import com.uravugal.matrimony.models.UserLead;
import com.uravugal.matrimony.repositories.UserLeadRepository;
import com.uravugal.matrimony.repositories.UserRepository;
import com.uravugal.matrimony.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserLeadRepository userLeadRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private com.uravugal.matrimony.repositories.BannedIdentifierRepository bannedIdentifierRepository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private static final int OTP_EXPIRY_MINUTES = 5;
    private static final int TOKEN_EXPIRY_MINUTES = 30;

    // ========= SEND OTP =========

    @Transactional
    public ResultResponse sendOtp(String email, String purpose, String firstNameHint) {
        ResultResponse response = new ResultResponse();
        try {
            if (email == null || email.isBlank()) {
                response.setCode(400);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("Email is required");
                return response;
            }

            String otp = String.format("%04d", new Random().nextInt(10000));

            if ("registration".equalsIgnoreCase(purpose)) {
                // Blacklist check — previously banned identifiers cannot re-register
                if (bannedIdentifierRepository.findByEmail(email) != null) {
                    response.setCode(403);
                    response.setStatus(ResponseStatus.FAILURE);
                    response.setMessage("This email cannot be used to create a new account. Please contact support if you believe this is a mistake.");
                    return response;
                }

                // Block if email already a real registered user
                UserEntity existingUser = userRepository.findByEmail(email);
                if (existingUser != null) {
                    response.setCode(409);
                    response.setStatus(ResponseStatus.FAILURE);
                    response.setMessage("This email is already registered. Please login instead.");
                    return response;
                }

                // Upsert into user_leads
                UserLead lead = userLeadRepository.findByEmail(email);
                if (lead == null) {
                    lead = new UserLead();
                    lead.setEmail(email);
                }
                lead.setOtp(otp);
                lead.setOtpCreatedAt(LocalDateTime.now());
                lead.setEmailVerified("N");
                lead.setVerificationToken(null);
                lead.setVerificationTokenExpiry(null);
                userLeadRepository.save(lead);

                String regName = (firstNameHint != null && !firstNameHint.isBlank()) ? firstNameHint : "there";
                emailService.sendOtpEmail(email, regName, otp, purpose);

            } else if ("reset".equalsIgnoreCase(purpose)) {
                UserEntity user = userRepository.findByEmail(email);
                if (user == null) {
                    response.setCode(404);
                    response.setStatus(ResponseStatus.FAILURE);
                    response.setMessage("No account found with this email");
                    return response;
                }
                user.setOtp(Integer.parseInt(otp));
                user.setOtpCreatedAt(LocalDateTime.now());
                userRepository.save(user);

                String firstName = user.getFirstName() != null ? user.getFirstName() : "there";
                emailService.sendOtpEmail(email, firstName, otp, purpose);

            } else {
                response.setCode(400);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("Invalid purpose. Must be 'registration' or 'reset'.");
                return response;
            }

            Map<String, Object> data = new HashMap<>();
            data.put("email", email);
            data.put("purpose", purpose);
            // DEV MODE: return OTP in response. Remove once Zeptomail is live in prod.
            data.put("otp", otp);

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("OTP sent successfully");
            response.setData(data);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error sending OTP: " + e.getMessage());
        }
        return response;
    }

    // ========= VERIFY OTP =========

    @Transactional
    public ResultResponse verifyOtp(String email, String otp, String purpose) {
        ResultResponse response = new ResultResponse();
        try {
            Map<String, Object> data = new HashMap<>();

            if ("registration".equalsIgnoreCase(purpose)) {
                UserLead lead = userLeadRepository.findByEmail(email);
                if (lead == null) {
                    response.setCode(404);
                    response.setStatus(ResponseStatus.FAILURE);
                    response.setMessage("Email not found. Please request a new OTP.");
                    return response;
                }
                if (lead.getOtpCreatedAt() == null ||
                        lead.getOtpCreatedAt().plusMinutes(OTP_EXPIRY_MINUTES).isBefore(LocalDateTime.now())) {
                    response.setCode(400);
                    response.setStatus(ResponseStatus.FAILURE);
                    response.setMessage("OTP has expired. Please request a new one.");
                    return response;
                }
                if (lead.getOtp() == null || !lead.getOtp().equals(otp)) {
                    response.setCode(400);
                    response.setStatus(ResponseStatus.FAILURE);
                    response.setMessage("Invalid OTP");
                    return response;
                }

                String verificationToken = UUID.randomUUID().toString();
                lead.setOtp(null);
                lead.setOtpCreatedAt(null);
                lead.setVerificationToken(verificationToken);
                lead.setVerificationTokenExpiry(LocalDateTime.now().plusMinutes(TOKEN_EXPIRY_MINUTES));
                lead.setEmailVerified("Y");
                userLeadRepository.save(lead);

                data.put("verified", true);
                data.put("verificationToken", verificationToken);

            } else if ("reset".equalsIgnoreCase(purpose)) {
                UserEntity user = userRepository.findByEmail(email);
                if (user == null) {
                    response.setCode(404);
                    response.setStatus(ResponseStatus.FAILURE);
                    response.setMessage("Email not found. Please request a new OTP.");
                    return response;
                }
                if (user.getOtpCreatedAt() == null ||
                        user.getOtpCreatedAt().plusMinutes(OTP_EXPIRY_MINUTES).isBefore(LocalDateTime.now())) {
                    response.setCode(400);
                    response.setStatus(ResponseStatus.FAILURE);
                    response.setMessage("OTP has expired. Please request a new one.");
                    return response;
                }
                if (user.getOtp() == null || !user.getOtp().toString().equals(otp)) {
                    response.setCode(400);
                    response.setStatus(ResponseStatus.FAILURE);
                    response.setMessage("Invalid OTP");
                    return response;
                }

                String resetToken = UUID.randomUUID().toString();
                user.setOtp(null);
                user.setOtpCreatedAt(null);
                user.setResetToken(resetToken);
                user.setResetTokenExpiry(LocalDateTime.now().plusMinutes(TOKEN_EXPIRY_MINUTES));
                userRepository.save(user);

                data.put("verified", true);
                data.put("resetToken", resetToken);

            } else {
                response.setCode(400);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("Invalid purpose. Must be 'registration' or 'reset'.");
                return response;
            }

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("OTP verified successfully");
            response.setData(data);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error verifying OTP: " + e.getMessage());
        }
        return response;
    }

    // ========= REFRESH TOKEN =========

    @Transactional
    public ResultResponse refreshAccessToken(String refreshToken) {
        ResultResponse response = new ResultResponse();
        try {
            if (refreshToken == null || refreshToken.isBlank()) {
                response.setCode(400);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("Refresh token is required");
                return response;
            }

            // Validate JWT signature/expiry first
            if (!jwtUtil.isTokenValid(refreshToken)) {
                response.setCode(401);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("Refresh token expired or invalid");
                return response;
            }

            // Match against persisted token (so logout/revocation works)
            UserEntity user = userRepository.findByRefreshToken(refreshToken);
            if (user == null) {
                response.setCode(401);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("Refresh token not recognised");
                return response;
            }

            if (user.getRefreshTokenExpiry() == null || user.getRefreshTokenExpiry().isBefore(LocalDateTime.now())) {
                response.setCode(401);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("Refresh token expired");
                return response;
            }

            if (user.getUserStatus() != com.uravugal.matrimony.enums.ApprovalStatus.APPROVED) {
                response.setCode(403);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("Account is not active or approved");
                return response;
            }

            // Issue new access token + rotate refresh token
            String newAccessToken = jwtUtil.generateToken(
                    user.getUserId(),
                    user.getMobile(),
                    user.getIsUser() != null ? user.getIsUser().name() : "FA"
            );
            String newRefreshToken = jwtUtil.generateRefreshToken(user.getUserId());
            user.setRefreshToken(newRefreshToken);
            user.setRefreshTokenExpiry(LocalDateTime.now().plusDays(60));
            userRepository.save(user);

            Map<String, Object> data = new HashMap<>();
            data.put("token", newAccessToken);
            data.put("refreshToken", newRefreshToken);

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Token refreshed");
            response.setData(data);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error refreshing token: " + e.getMessage());
        }
        return response;
    }

    @Transactional
    public ResultResponse logout(String refreshToken) {
        ResultResponse response = new ResultResponse();
        try {
            if (refreshToken != null && !refreshToken.isBlank()) {
                UserEntity user = userRepository.findByRefreshToken(refreshToken);
                if (user != null) {
                    user.setRefreshToken(null);
                    user.setRefreshTokenExpiry(null);
                    userRepository.save(user);
                }
            }
            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Logged out");
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error logging out: " + e.getMessage());
        }
        return response;
    }

    // ========= RESET PASSWORD =========

    @Transactional
    public ResultResponse resetPassword(String resetToken, String newPassword) {
        ResultResponse response = new ResultResponse();
        try {
            UserEntity user = userRepository.findByResetToken(resetToken);
            if (user == null) {
                response.setCode(400);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("Invalid reset token");
                return response;
            }

            if (user.getResetTokenExpiry() == null ||
                user.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
                response.setCode(400);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("Reset token has expired. Please request a new one.");
                return response;
            }

            user.setPin(passwordEncoder.encode(newPassword));
            user.setResetToken(null);
            user.setResetTokenExpiry(null);
            userRepository.save(user);

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Password reset successfully. Please login with your new PIN.");
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error resetting password: " + e.getMessage());
        }
        return response;
    }
}