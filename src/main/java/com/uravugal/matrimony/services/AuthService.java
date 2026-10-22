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
    private WhatsAppService whatsAppService;

    @Autowired
    private com.uravugal.matrimony.repositories.MobileOtpRepository mobileOtpRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private com.uravugal.matrimony.repositories.BannedIdentifierRepository bannedIdentifierRepository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private static final int OTP_EXPIRY_MINUTES = 5;
    private static final int TOKEN_EXPIRY_MINUTES = 30;

    // ========= SEND OTP =========

    /**
     * "add-email" (2026-10-12): a signed-in member whose account has NO email (the
     * imported broker profiles) verifying one from the edit page. Same lead-table
     * OTP + verificationToken path as registration; the token is then spent by
     * UserService.addEmail, which is the only place that writes users.email.
     */
    private static boolean isLeadPurpose(String purpose) {
        // "change-email" (2026-10-20): same path, for a member REPLACING their email from the
        // edit page; the token is spent by UserService.changeEmail.
        return "registration".equalsIgnoreCase(purpose) || "add-email".equalsIgnoreCase(purpose)
                || "change-email".equalsIgnoreCase(purpose);
    }

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

            if (isLeadPurpose(purpose)) {
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
                if (!emailService.sendOtpEmail(email, regName, otp, purpose)) {
                    // Both attempts failed — say so instead of "OTP sent successfully"
                    // while the member waits for a mail that never left.
                    response.setCode(502);
                    response.setStatus(ResponseStatus.FAILURE);
                    response.setMessage("We couldn't send the verification email right now. Please try again in a moment.");
                    return response;
                }

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

                // Full name — every outbound mail addresses the member by both names.
                String firstName = java.util.stream.Stream
                        .of(user.getFirstName(), user.getLastName())
                        .filter(x -> x != null && !x.isBlank())
                        .map(String::trim)
                        .reduce((x, y) -> x + " " + y)
                        .orElse("there");
                if (!emailService.sendOtpEmail(email, firstName, otp, purpose)) {
                    response.setCode(502);
                    response.setStatus(ResponseStatus.FAILURE);
                    response.setMessage("We couldn't send the verification email right now. Please try again in a moment.");
                    return response;
                }

            } else {
                response.setCode(400);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("Invalid purpose. Must be 'registration', 'add-email', 'change-email' or 'reset'.");
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

            if (isLeadPurpose(purpose)) {
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
                response.setMessage("Invalid purpose. Must be 'registration', 'add-email', 'change-email' or 'reset'.");
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

    // ========= MOBILE OTP (WhatsApp) =========

    private static final int MOBILE_OTP_MAX_ATTEMPTS = 5;

    private static boolean isMobilePurpose(String purpose) {
        return "registration".equalsIgnoreCase(purpose) || "add-mobile".equalsIgnoreCase(purpose)
                || "lead".equalsIgnoreCase(purpose) || "reset".equalsIgnoreCase(purpose)
                || "start".equalsIgnoreCase(purpose);
    }

    /** A website quick-register row: name + mobile only, never went through sign-up. */
    static boolean isStarter(UserEntity u) {
        return u != null && u.getUserStatus() == null;
    }

    /**
     * Sends a 6-digit code on WhatsApp (2026-10-14). Purposes: "registration" (the
     * sign-up form, before a user row exists) and "add-mobile" (a signed-in member
     * whose account has no mobile — the imported profiles). Both refuse a number
     * that already belongs to an account, since mobile is the login identity.
     */
    @Transactional
    public ResultResponse sendMobileOtp(String mobile, String purpose) {
        ResultResponse response = new ResultResponse();
        response.setStatus(ResponseStatus.FAILURE);
        try {
            if (mobile == null || !mobile.matches("^[6-9]\\d{9}$")) {
                response.setCode(400);
                response.setMessage("Please enter a valid 10-digit mobile number");
                return response;
            }
            if (!isMobilePurpose(purpose)) {
                response.setCode(400);
                response.setMessage("Invalid purpose. Must be 'registration', 'add-mobile', 'lead' or 'reset'.");
                return response;
            }
            if (bannedIdentifierRepository.findByMobile(mobile) != null) {
                response.setCode(403);
                response.setMessage("This mobile number cannot be used. Please contact support if you believe this is a mistake.");
                return response;
            }
            UserEntity existing = userRepository.findByMobile(mobile);
            boolean reset = "reset".equalsIgnoreCase(purpose);
            if (reset) {
                // Forgot PIN: the number must belong to a real account.
                if (existing == null || isStarter(existing)) {
                    response.setCode(404);
                    response.setMessage("No account found with this mobile number");
                    return response;
                }
            } else if (existing != null && RegistrationService.isIncomplete(existing) && !"start".equalsIgnoreCase(purpose)) {
                // New flow (2026-10-19): the number already began a registration (and chose a
                // PIN at the number page). Sign Up must not restart it — the member signs in with
                // number + PIN and is taken back to the pending sections. A row started BEFORE
                // the PIN moved to that page has no PIN and cannot be resumed either way; such a
                // row must be cleared by support (the client chose the strict rule).
                response.setCode(409);
                response.setMessage("This number is already registered. Please sign in to complete your profile.");
                return response;
            } else if (existing != null && !isStarter(existing) && !"start".equalsIgnoreCase(purpose)) {
                // A website lead (starter row) may go on to register; a real account may not.
                // "start" (the phone-first entry screen, 2026-10-16) accepts any number: a
                // registered one logs in with the code, a new one goes on to sign-up.
                response.setCode(409);
                response.setMessage("This mobile number is already registered. Please login instead.");
                return response;
            }

            String otp = String.format("%06d", new java.security.SecureRandom().nextInt(1_000_000));
            com.uravugal.matrimony.models.MobileOtp row = mobileOtpRepository.findByMobile(mobile);
            if (row == null) {
                row = new com.uravugal.matrimony.models.MobileOtp();
                row.setMobile(mobile);
            }
            row.setOtp(otp);
            row.setOtpCreatedAt(LocalDateTime.now());
            row.setAttempts(0);
            row.setVerified("N");
            row.setVerificationToken(null);
            row.setVerificationTokenExpiry(null);
            mobileOtpRepository.save(row);

            boolean sent = reset
                    ? whatsAppService.sendForgotPinOtp(mobile, otp, "vvm-reset-" + row.getId())
                    : whatsAppService.sendOtp(mobile, otp, "vvm-" + purpose + "-" + row.getId());
            if (!sent) {
                response.setCode(502);
                response.setMessage("We couldn't send the WhatsApp code right now. Please try again in a moment.");
                System.out.println("[WhatsApp] send failed for " + mobile + ": " + whatsAppService.getLastError());
                return response;
            }

            Map<String, Object> data = new HashMap<>();
            data.put("mobile", mobile);
            data.put("purpose", purpose);
            data.put("channel", "whatsapp");
            data.put("exists", existing != null && !isStarter(existing));
            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("OTP sent on WhatsApp");
            response.setData(data);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Error sending OTP: " + e.getMessage());
        }
        return response;
    }

    @Transactional
    public ResultResponse verifyMobileOtp(String mobile, String otp, String purpose) {
        ResultResponse response = new ResultResponse();
        response.setStatus(ResponseStatus.FAILURE);
        try {
            if (!isMobilePurpose(purpose)) {
                response.setCode(400);
                response.setMessage("Invalid purpose. Must be 'registration' or 'add-mobile'.");
                return response;
            }
            com.uravugal.matrimony.models.MobileOtp row = mobileOtpRepository.findByMobile(mobile);
            if (row == null || row.getOtp() == null) {
                response.setCode(404);
                response.setMessage("No code was sent to this number. Please request a new one.");
                return response;
            }
            if (row.getOtpCreatedAt() == null ||
                    row.getOtpCreatedAt().plusMinutes(OTP_EXPIRY_MINUTES).isBefore(LocalDateTime.now())) {
                response.setCode(400);
                response.setMessage("OTP has expired. Please request a new one.");
                return response;
            }
            if (row.getAttempts() >= MOBILE_OTP_MAX_ATTEMPTS) {
                response.setCode(400);
                response.setMessage("Too many wrong attempts. Please request a new code.");
                return response;
            }
            if (!row.getOtp().equals(otp == null ? "" : otp.trim())) {
                row.setAttempts(row.getAttempts() + 1);
                mobileOtpRepository.save(row);
                response.setCode(400);
                response.setMessage("Invalid OTP");
                return response;
            }

            String token = UUID.randomUUID().toString();
            row.setOtp(null);
            row.setOtpCreatedAt(null);
            row.setAttempts(0);
            row.setVerified("Y");
            row.setVerificationToken(token);
            row.setVerificationTokenExpiry(LocalDateTime.now().plusMinutes(TOKEN_EXPIRY_MINUTES));
            mobileOtpRepository.save(row);

            Map<String, Object> data = new HashMap<>();
            data.put("verified", true);
            if ("reset".equalsIgnoreCase(purpose)) {
                // Forgot PIN on WhatsApp: hand back the same resetToken the email path mints,
                // so /auth/reset-password and SetNewPasswordScreen are unchanged.
                UserEntity user = userRepository.findByMobile(mobile);
                if (user == null || isStarter(user)) {
                    response.setCode(404);
                    response.setMessage("No account found with this mobile number");
                    return response;
                }
                String resetToken = UUID.randomUUID().toString();
                user.setResetToken(resetToken);
                user.setResetTokenExpiry(LocalDateTime.now().plusMinutes(TOKEN_EXPIRY_MINUTES));
                userRepository.save(user);
                data.put("resetToken", resetToken);
            } else {
                data.put("verificationToken", token);
                // A website lead registering in the app gets its name back for prefill.
                UserEntity lead = userRepository.findByMobile(mobile);
                data.put("exists", lead != null && !isStarter(lead));
                // New registration flow (2026-10-19): an INCOMPLETE row resumes on any phone —
                // a fresh registrationToken + the saved draft come back with the verification.
                if (RegistrationService.isIncomplete(lead)) {
                    data.put("registration", RegistrationService.resumePayload(lead, userRepository, true));
                }
                if (isStarter(lead)) {
                    Map<String, Object> l = new HashMap<>();
                    l.put("firstName", lead.getFirstName());
                    l.put("profileFor", lead.getProfileCreated());
                    data.put("lead", l);
                }
            }
            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Mobile number verified");
            response.setData(data);
        } catch (Exception e) {
            response.setCode(500);
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