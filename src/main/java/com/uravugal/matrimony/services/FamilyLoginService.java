package com.uravugal.matrimony.services;

import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.enums.SubscriptionStatus;
import com.uravugal.matrimony.models.FamilyLogin;
import com.uravugal.matrimony.models.Features;
import com.uravugal.matrimony.models.PlanFeatures;
import com.uravugal.matrimony.models.UserEntity;
import com.uravugal.matrimony.models.UserSubscriptions;
import com.uravugal.matrimony.repositories.FamilyLoginRepository;
import com.uravugal.matrimony.repositories.FeaturesRepository;
import com.uravugal.matrimony.repositories.PlanFeaturesRepository;
import com.uravugal.matrimony.repositories.UserRepository;
import com.uravugal.matrimony.repositories.UserSubscriptionsRepository;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FamilyLoginService {

    @Autowired
    private FamilyLoginRepository familyLoginRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSubscriptionsRepository userSubscriptionsRepository;

    @Autowired
    private FeaturesRepository featuresRepository;

    @Autowired
    private PlanFeaturesRepository planFeaturesRepository;

    @Autowired
    private com.uravugal.matrimony.utils.JwtUtil jwtUtil;

    // ============================================================
    // CREATE — primary user adds a family login (Gold+ only)
    // ============================================================
    public ResultResponse createFamilyLogin(String encodedPrimaryUserId, FamilyLogin input) {
        ResultResponse resp = new ResultResponse();
        try {
            Long primaryUserId = Long.parseLong(new String(Base64.getDecoder().decode(encodedPrimaryUserId)));

            // Plan gate: FAMILY_LOGIN feature must be in primary user's plan (Gold+)
            List<UserSubscriptions> subs = userSubscriptionsRepository
                    .findByUserIdAndStatus(primaryUserId, SubscriptionStatus.ACTIVE);
            if (subs.isEmpty() || subs.get(0).getSubscriptionPlanId() == 1L) {
                resp.setCode(403);
                resp.setStatus(ResponseStatus.FAILURE);
                resp.setMessage("PLAN_UPGRADE_REQUIRED");
                return resp;
            }
            Features feature = featuresRepository.findByCode("FAMILY_LOGIN");
            if (feature != null) {
                PlanFeatures pf = planFeaturesRepository.findByFeatureIdAndSubscriptionPlanId(
                        feature.getId(), subs.get(0).getSubscriptionPlanId());
                if (pf == null) {
                    resp.setCode(403);
                    resp.setStatus(ResponseStatus.FAILURE);
                    resp.setMessage("PLAN_UPGRADE_REQUIRED");
                    return resp;
                }
            }

            if (input.getMobile() == null || input.getMobile().isBlank()) {
                resp.setCode(400); resp.setStatus(ResponseStatus.FAILURE);
                resp.setMessage("Mobile is required"); return resp;
            }
            if (input.getPin() == null || input.getPin().isBlank()) {
                resp.setCode(400); resp.setStatus(ResponseStatus.FAILURE);
                resp.setMessage("PIN is required"); return resp;
            }
            if (input.getParentName() == null || input.getParentName().isBlank()) {
                resp.setCode(400); resp.setStatus(ResponseStatus.FAILURE);
                resp.setMessage("Parent name is required"); return resp;
            }

            // Prevent collision with existing user mobile
            UserEntity existing = userRepository.findByMobile(input.getMobile());
            if (existing != null) {
                resp.setCode(409); resp.setStatus(ResponseStatus.FAILURE);
                resp.setMessage("MOBILE_EXISTS_AS_MEMBER"); return resp;
            }
            FamilyLogin existingFamily = familyLoginRepository.findByMobileAndStatus(input.getMobile(), "ACTIVE");
            if (existingFamily != null) {
                resp.setCode(409); resp.setStatus(ResponseStatus.FAILURE);
                resp.setMessage("MOBILE_EXISTS_AS_FAMILY"); return resp;
            }
            // Email collision (only if email provided)
            if (input.getEmail() != null && !input.getEmail().isBlank()) {
                UserEntity existingEmail = userRepository.findByEmail(input.getEmail());
                if (existingEmail != null) {
                    resp.setCode(409); resp.setStatus(ResponseStatus.FAILURE);
                    resp.setMessage("EMAIL_EXISTS_AS_MEMBER"); return resp;
                }
            }

            FamilyLogin fl = new FamilyLogin();
            fl.setPrimaryUserId(primaryUserId);
            fl.setParentName(input.getParentName());
            fl.setRelationship(input.getRelationship());
            fl.setMobile(input.getMobile());
            fl.setEmail(input.getEmail());
            // Store PIN base64-encoded to match the UserEntity scheme
            fl.setPin(Base64.getEncoder().encodeToString(input.getPin().getBytes()));
            fl.setStatus("ACTIVE");
            FamilyLogin saved = familyLoginRepository.save(fl);
            // Don't leak PIN
            saved.setPin(null);

            resp.setCode(200);
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setMessage("Family login created. Share the mobile + PIN with your family member.");
            resp.setData(saved);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setStatus(ResponseStatus.FAILURE);
            resp.setMessage("Error: " + e.getMessage());
        }
        return resp;
    }

    // ============================================================
    // LIST — primary user sees their own family logins
    // ============================================================
    public ResultResponse listMine(String encodedPrimaryUserId) {
        ResultResponse resp = new ResultResponse();
        try {
            Long primaryUserId = Long.parseLong(new String(Base64.getDecoder().decode(encodedPrimaryUserId)));
            List<FamilyLogin> rows = familyLoginRepository
                    .findByPrimaryUserIdAndStatusOrderByIdDesc(primaryUserId, "ACTIVE");
            rows.forEach(r -> r.setPin(null));
            resp.setCode(200);
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setMessage("Family logins fetched");
            resp.setData(rows);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setStatus(ResponseStatus.FAILURE);
            resp.setMessage("Error: " + e.getMessage());
        }
        return resp;
    }

    // ============================================================
    // REVOKE — primary user disables a family login
    // ============================================================
    public ResultResponse revoke(String encodedPrimaryUserId, Long familyLoginId) {
        ResultResponse resp = new ResultResponse();
        try {
            Long primaryUserId = Long.parseLong(new String(Base64.getDecoder().decode(encodedPrimaryUserId)));
            FamilyLogin fl = familyLoginRepository.findById(familyLoginId).orElse(null);
            if (fl == null) {
                resp.setCode(404); resp.setStatus(ResponseStatus.FAILURE);
                resp.setMessage("Family login not found"); return resp;
            }
            if (!fl.getPrimaryUserId().equals(primaryUserId)) {
                resp.setCode(403); resp.setStatus(ResponseStatus.FAILURE);
                resp.setMessage("Not authorized to revoke this family login"); return resp;
            }
            fl.setStatus("REVOKED");
            familyLoginRepository.save(fl);
            resp.setCode(200);
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setMessage("Family login revoked");
        } catch (Exception e) {
            resp.setCode(500);
            resp.setStatus(ResponseStatus.FAILURE);
            resp.setMessage("Error: " + e.getMessage());
        }
        return resp;
    }

    // ============================================================
    // LOGIN — used as a fall-through from UserService.login
    // Returns the SAME payload shape as a regular user login,
    // but userId / profile fields are the PRIMARY user's,
    // and role = "PARENT".
    // ============================================================
    public Map<String, Object> tryLogin(String mobile, String plainPin) {
        FamilyLogin fl = familyLoginRepository.findByMobileAndStatus(mobile, "ACTIVE");
        if (fl == null) return null;

        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(fl.getPin()));
        } catch (Exception e) { return null; }
        if (!decoded.equals(plainPin)) return null;

        UserEntity primary = userRepository.findById(fl.getPrimaryUserId()).orElse(null);
        if (primary == null || primary.getUserStatus() != com.uravugal.matrimony.enums.ApprovalStatus.APPROVED) return null;

        // Update last login timestamp
        fl.setLastLoginAt(java.time.LocalDateTime.now());
        familyLoginRepository.save(fl);

        // Issue JWT tokens scoped to the primary (child) user so all /user/* calls work
        String accessToken = jwtUtil.generateToken(
                primary.getUserId(),
                primary.getMobile(),
                primary.getIsUser() != null ? primary.getIsUser().name() : "FA"
        );
        String refreshToken = jwtUtil.generateRefreshToken(primary.getUserId());
        primary.setRefreshToken(refreshToken);
        primary.setRefreshTokenExpiry(java.time.LocalDateTime.now().plusDays(60));
        userRepository.save(primary);

        String encodedChildId = Base64.getEncoder()
                .encodeToString(String.valueOf(primary.getUserId()).getBytes());

        Map<String, Object> out = new HashMap<>();
        out.put("userId", encodedChildId);
        out.put("token", accessToken);
        out.put("refreshToken", refreshToken);
        out.put("email", primary.getEmail());
        out.put("gender", primary.getGender() != null ? primary.getGender().name() : null);
        out.put("dob", primary.getDob() != null ? primary.getDob().toString() : null);
        out.put("location", primary.getLocation());
        out.put("mobile", primary.getMobile());
        out.put("firstName", primary.getFirstName());
        out.put("lastName", primary.getLastName());
        out.put("profileImage", primary.getProfileImage());
        out.put("casteId", primary.getCasteId());
        out.put("isUser", primary.getIsUser() != null ? primary.getIsUser().name() : null);
        out.put("userStatus", primary.getUserStatus() != null ? primary.getUserStatus().toString() : "APPROVED");
        out.put("rejectionReason", primary.getRejectionReason());
        // Parent-specific fields
        out.put("role", "PARENT");
        out.put("familyLoginId", fl.getId());
        out.put("parentName", fl.getParentName());
        out.put("relationship", fl.getRelationship());
        return out;
    }

    // ============================================================
    // ADMIN — list all, revoke by id
    // ============================================================
    public ResultResponse adminList() {
        ResultResponse resp = new ResultResponse();
        try {
            List<FamilyLogin> rows = familyLoginRepository.findAllByOrderByIdDesc();

            // Enrich with the primary user's own name/mobile — the raw primaryUserId alone
            // isn't enough for an admin to identify whose family login they're looking at.
            List<Map<String, Object>> enriched = new ArrayList<>();
            for (FamilyLogin r : rows) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", r.getId());
                row.put("primaryUserId", r.getPrimaryUserId());
                row.put("parentName", r.getParentName());
                row.put("relationship", r.getRelationship());
                row.put("mobile", r.getMobile());
                row.put("email", r.getEmail());
                row.put("status", r.getStatus());
                row.put("lastLoginAt", r.getLastLoginAt());
                row.put("createdAt", r.getCreatedAt());
                // GenericEntity's @PreUpdate bumps updatedAt on every save, including the
                // revoke() / adminRevoke() status flip — so this doubles as "revoked at"
                // for REVOKED rows without needing a dedicated column.
                row.put("updatedAt", r.getUpdatedAt());

                try {
                    UserEntity primary = userRepository.findById(r.getPrimaryUserId()).orElse(null);
                    if (primary != null) {
                        row.put("primaryUserName", ((primary.getFirstName() == null ? "" : primary.getFirstName()) + " " +
                                (primary.getLastName() == null ? "" : primary.getLastName())).trim());
                        row.put("primaryUserMobile", primary.getMobile());
                    }
                } catch (Exception ignored) {}

                enriched.add(row);
            }

            resp.setCode(200);
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setMessage("All family logins");
            resp.setData(enriched);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setStatus(ResponseStatus.FAILURE);
            resp.setMessage("Error: " + e.getMessage());
        }
        return resp;
    }

    public ResultResponse adminRevoke(Long id) {
        ResultResponse resp = new ResultResponse();
        try {
            FamilyLogin fl = familyLoginRepository.findById(id).orElse(null);
            if (fl == null) {
                resp.setCode(404); resp.setStatus(ResponseStatus.FAILURE);
                resp.setMessage("Family login not found"); return resp;
            }
            fl.setStatus("REVOKED");
            familyLoginRepository.save(fl);
            resp.setCode(200);
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setMessage("Family login revoked");
        } catch (Exception e) {
            resp.setCode(500);
            resp.setStatus(ResponseStatus.FAILURE);
            resp.setMessage("Error: " + e.getMessage());
        }
        return resp;
    }
}