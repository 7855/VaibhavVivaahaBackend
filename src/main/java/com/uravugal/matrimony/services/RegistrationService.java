package com.uravugal.matrimony.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.dtos.UserProfileRequest;
import com.uravugal.matrimony.enums.IsUser;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.MobileOtp;
import com.uravugal.matrimony.models.UserEntity;
import com.uravugal.matrimony.repositories.BannedIdentifierRepository;
import com.uravugal.matrimony.repositories.MobileOtpRepository;
import com.uravugal.matrimony.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * The NEW app registration flow (2026-10-19) — a separate API so the old sign-up page and
 * /user/createUser are untouched.
 *
 *   POST /auth/register/start     name + profile-for + mobile (WhatsApp-verified) → the users row
 *                                 is created at once with registrationStage = INCOMPLETE
 *   POST /auth/register/step      saves the draft of a step (JSON) + which steps are done
 *   POST /auth/register/complete  PIN + everything → builds the same UserProfileRequest the old
 *                                 page sends and calls UserService.createUser, which CLAIMS the
 *                                 row (userStatus NULL → PENDING, user_details, Free plan,
 *                                 welcome WhatsApp). Stage → COMPLETE.
 *
 * Resume: /auth/verify-mobile-otp (and OTP login) answer `registration` for an INCOMPLETE row —
 * a fresh registrationToken + the draft — so any phone can continue at the right step.
 */
@Service
public class RegistrationService {

    public static final String STAGE_INCOMPLETE = "INCOMPLETE";
    public static final String STAGE_COMPLETE = "COMPLETE";
    private static final int MOBILE_TOKEN_MINUTES = 30;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired private UserRepository userRepository;
    @Autowired private MobileOtpRepository mobileOtpRepository;
    @Autowired private BannedIdentifierRepository bannedIdentifierRepository;
    @Autowired private UserService userService;

    /* ---------------- helpers shared with AuthService / UserService.login ---------------- */

    public static boolean isIncomplete(UserEntity u) {
        return u != null && STAGE_INCOMPLETE.equals(u.getRegistrationStage());
    }

    /**
     * What the app needs to reopen the pending sections. `mintToken` = a NEW phone session
     * (WhatsApp code just verified) → a fresh token that invalidates older ones; otherwise the
     * current token is returned (minted only if the row never had one).
     */
    public static Map<String, Object> resumePayload(UserEntity u, UserRepository repo, boolean mintToken) {
        if (mintToken || u.getRegistrationToken() == null) {
            u.setRegistrationToken(UUID.randomUUID().toString());
            repo.save(u);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", Base64.getEncoder().encodeToString(String.valueOf(u.getUserId()).getBytes()));
        m.put("registrationToken", u.getRegistrationToken());
        m.put("firstName", u.getFirstName());
        m.put("lastName", u.getLastName());
        m.put("mobile", u.getMobile());
        m.put("profileFor", u.getProfileCreated());
        m.put("draft", parseDraft(u.getRegistrationDraft()));
        return m;
    }

    private static Map<String, Object> parseDraft(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try { return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {}); }
        catch (Exception e) { return new LinkedHashMap<>(); }
    }

    private static ResultResponse fail(int code, String msg) {
        ResultResponse r = new ResultResponse();
        r.setCode(code); r.setStatus(ResponseStatus.FAILURE); r.setMessage(msg);
        return r;
    }

    private boolean mobileTokenOk(String mobile, String token) {
        if (mobile == null || token == null || token.isBlank()) return false;
        MobileOtp row = mobileOtpRepository.findByMobile(mobile);
        return row != null && "Y".equals(row.getVerified()) && token.equals(row.getVerificationToken())
                && row.getVerificationTokenExpiry() != null && row.getVerificationTokenExpiry().isAfter(LocalDateTime.now());
    }

    /** Looks the row up by its base64 id and checks the registration token. */
    private UserEntity authorised(String encodedUserId, String registrationToken) {
        try {
            Long id = Long.parseLong(new String(Base64.getDecoder().decode(encodedUserId)));
            UserEntity u = userRepository.findById(id).orElse(null);
            if (!isIncomplete(u) || registrationToken == null || !registrationToken.equals(u.getRegistrationToken())) return null;
            return u;
        } catch (Exception e) { return null; }
    }

    /* ---------------- start ---------------- */

    public ResultResponse start(Map<String, Object> body) {
        try {
            String mobile = str(body.get("mobile")), token = str(body.get("mobileVerificationToken"));
            String first = str(body.get("firstName")), last = str(body.get("lastName")), pf = str(body.get("profileFor"));
            String pin = str(body.get("pin"));
            if (mobile == null || !mobile.matches("^[6-9]\\d{9}$")) return fail(400, "Please enter a valid 10-digit mobile number");
            if (first == null || first.length() < 2) return fail(400, "First name is required");
            // The PIN is chosen on the number page (client, 2026-10-19 later): it is stored on the
            // INCOMPLETE row right away so the member can sign in with number + PIN on any phone
            // and be taken back to the pending sections. Same Base64 form `createUser` writes.
            if (pin == null || !pin.matches("^\\d{4}$")) return fail(400, "Choose a 4-digit PIN");
            if (!mobileTokenOk(mobile, token)) return fail(403, "MOBILE_NOT_VERIFIED");
            if (bannedIdentifierRepository.findByMobile(mobile) != null)
                return fail(403, "This mobile number cannot be used to create a new account. Please contact support if you believe this is a mistake.");

            UserEntity u = userRepository.findByMobile(mobile);
            if (u != null && u.getUserStatus() != null) return fail(409, "This mobile number is already registered. Please sign in instead.");
            boolean fresh = u == null;
            if (fresh) {
                u = new UserEntity();
                u.setMobile(mobile);
                u.setIsUser(IsUser.FA);
                u.setIsBlocked('N');
                u.setViewCount(0);
                u.setOnline(false);
            }
            u.setFirstName(first);
            u.setLastName(last);
            if (pf != null) u.setProfileCreated(pf);
            u.setPin(Base64.getEncoder().encodeToString(pin.getBytes()));
            u.setLeadVerified("Y");
            if (u.getLeadStatus() == null) u.setLeadStatus("NEW");
            u.setRegistrationStage(STAGE_INCOMPLETE);
            u = userRepository.save(u);

            ResultResponse r = new ResultResponse();
            r.setCode(200); r.setStatus(ResponseStatus.SUCCESS);
            r.setMessage(fresh ? "Registration started" : "Registration resumed");
            r.setData(resumePayload(u, userRepository, true));
            return r;
        } catch (Exception e) {
            return fail(500, "Could not start registration: " + e.getMessage());
        }
    }

    /* ---------------- step ---------------- */

    public ResultResponse saveStep(Map<String, Object> body) {
        try {
            UserEntity u = authorised(str(body.get("userId")), str(body.get("registrationToken")));
            if (u == null) return fail(403, "REGISTRATION_TOKEN_INVALID");
            Object draft = body.get("draft");
            Map<String, Object> merged = parseDraft(u.getRegistrationDraft());
            if (draft instanceof Map) merged.putAll((Map<String, Object>) draft);
            if (body.get("doneSteps") != null) merged.put("doneSteps", body.get("doneSteps"));
            // The names / profile-for may be edited on later steps too — keep the row in sync.
            if (merged.get("firstName") != null) u.setFirstName(str(merged.get("firstName")));
            if (merged.get("lastName") != null) u.setLastName(str(merged.get("lastName")));
            if (merged.get("profileFor") != null) u.setProfileCreated(str(merged.get("profileFor")));
            u.setRegistrationDraft(MAPPER.writeValueAsString(merged));
            userRepository.save(u);
            ResultResponse r = new ResultResponse();
            r.setCode(200); r.setStatus(ResponseStatus.SUCCESS); r.setMessage("Saved");
            Map<String, Object> d = new HashMap<>(); d.put("doneSteps", merged.get("doneSteps"));
            r.setData(d);
            return r;
        } catch (Exception e) {
            return fail(500, "Could not save step: " + e.getMessage());
        }
    }

    /* ---------------- complete ---------------- */

    public ResultResponse complete(Map<String, Object> body) {
        try {
            UserEntity u = authorised(str(body.get("userId")), str(body.get("registrationToken")));
            if (u == null) return fail(403, "REGISTRATION_TOKEN_INVALID");

            // The old page's exact request, assembled here. createUser requires a WhatsApp token
            // for the number; the registration token already proves the number, so a fresh one is
            // minted server-side for this call (never handed to the client).
            MobileOtp row = mobileOtpRepository.findByMobile(u.getMobile());
            if (row == null) { row = new MobileOtp(); row.setMobile(u.getMobile()); }
            String fresh = UUID.randomUUID().toString();
            row.setVerified("Y"); row.setVerificationToken(fresh);
            row.setVerificationTokenExpiry(LocalDateTime.now().plusMinutes(MOBILE_TOKEN_MINUTES));
            mobileOtpRepository.save(row);

            UserProfileRequest req = new UserProfileRequest();
            req.setFirstName(firstNonBlank(str(body.get("firstName")), u.getFirstName()));
            req.setLastName(firstNonBlank(str(body.get("lastName")), u.getLastName()));
            req.setMobileNumber(u.getMobile());
            req.setMobileVerificationToken(fresh);
            req.setProfileFor(firstNonBlank(str(body.get("profileFor")), u.getProfileCreated()));
            req.setGender(str(body.get("gender")));
            String dob = str(body.get("dateOfBirth"));
            if (dob != null) req.setDateOfBirth(LocalDate.parse(dob));
            req.setAge(str(body.get("age")));
            req.setCasteId(body.get("casteId") == null ? null : Integer.valueOf(str(body.get("casteId"))));
            req.setSubcasteId(body.get("subcasteId") == null || str(body.get("subcasteId")) == null ? null : Long.valueOf(str(body.get("subcasteId"))));
            req.setLocation(str(body.get("location")));
            req.setEducation(str(body.get("education")));
            req.setOccupation(str(body.get("occupation")));
            req.setEmployingIn(str(body.get("employingIn")));
            // The PIN was stored on the row at start (Base64); createUser re-encodes a plain PIN.
            String pin = str(body.get("pin"));
            if (pin == null && u.getPin() != null) {
                try { pin = new String(Base64.getDecoder().decode(u.getPin())); } catch (IllegalArgumentException ignored) { }
            }
            if (pin == null) return fail(400, "PIN not set — please start the registration again");
            req.setPin(pin);
            req.setEmail(str(body.get("email")));
            req.setVerificationToken(str(body.get("verificationToken")));

            ResultResponse r = userService.createUser(req);
            if (r.getCode() == 200) {
                UserEntity done = userRepository.findById(u.getUserId()).orElse(u);
                done.setRegistrationStage(STAGE_COMPLETE);
                done.setRegistrationToken(null);
                done.setRegistrationDraft(null);
                userRepository.save(done);
            }
            return r;
        } catch (Exception e) {
            return fail(500, "Could not complete registration: " + e.getMessage());
        }
    }

    private static String str(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() || "null".equals(s) ? null : s;
    }
    private static String firstNonBlank(String a, String b) { return a != null ? a : b; }
}
