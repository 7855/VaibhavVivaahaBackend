package com.uravugal.matrimony.services;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uravugal.matrimony.dtos.AdminUserDetailDTO;
import com.uravugal.matrimony.dtos.PaginatedResultResponse;
import com.uravugal.matrimony.dtos.PaginationData;
import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ActiveStatus;
import com.uravugal.matrimony.enums.ApprovalStatus;
import com.uravugal.matrimony.enums.EmploymentType;
import com.uravugal.matrimony.enums.Gender;
import com.uravugal.matrimony.enums.IsUser;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.UserDetailEntity;
import com.uravugal.matrimony.models.UserEntity;
import com.uravugal.matrimony.repositories.UserDetailRepository;
import com.uravugal.matrimony.repositories.UserRepository;
import com.uravugal.matrimony.dtos.AdminApprovePaymentRequestDTO;
import com.uravugal.matrimony.enums.PaymentRequestStatus;
import com.uravugal.matrimony.models.PaymentRequestEntity;
import com.uravugal.matrimony.models.SubscriptionPlan;
import com.uravugal.matrimony.models.UserSubscriptions;
import com.uravugal.matrimony.repositories.PaymentRequestRepository;
import com.uravugal.matrimony.repositories.SubscriptionPlanRepository;
import com.uravugal.matrimony.repositories.UserSubscriptionsRepository;
import com.uravugal.matrimony.repositories.GalleryRepository;
import com.uravugal.matrimony.repositories.RestrictedFieldRequestRepository;
import com.uravugal.matrimony.repositories.UserReportRepository;
import com.uravugal.matrimony.models.GalleryEntity;
import com.uravugal.matrimony.models.RestrictedFieldRequest;
import com.uravugal.matrimony.enums.SubscriptionStatus;
import com.uravugal.matrimony.models.AuditLog;
import com.uravugal.matrimony.models.CmsPage;
import com.uravugal.matrimony.models.SupportTicket;
import com.uravugal.matrimony.models.TicketMessage;
import com.uravugal.matrimony.models.KeyValue;
import com.uravugal.matrimony.models.Notification;
import com.uravugal.matrimony.repositories.AuditLogRepository;
import com.uravugal.matrimony.repositories.CmsPageRepository;
import com.uravugal.matrimony.repositories.SupportTicketRepository;
import com.uravugal.matrimony.repositories.TicketMessageRepository;
import com.uravugal.matrimony.repositories.KeyValueRepository;
import com.uravugal.matrimony.repositories.NotificationRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;

@Service
public class AdminService {

    private static final String PREFIX = "UMR";

    @Autowired
    private UserRepository userRepository;

    @Autowired

    private WhatsAppService whatsAppService;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    @Autowired
    private UserDetailRepository userDetailRepository;

    @Autowired
    private PaymentRequestRepository paymentRequestRepository;

    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Autowired
    private UserSubscriptionsRepository userSubscriptionsRepository;

    @Autowired
    private PushNotificationService pushNotificationService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private GalleryRepository galleryRepository;

    @Autowired
    private RestrictedFieldRequestRepository restrictedFieldRequestRepository;

    @Autowired
    private UserReportRepository userReportRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private CmsPageRepository cmsPageRepository;

    @Autowired
    private SupportTicketRepository supportTicketRepository;

    @Autowired
    private TicketMessageRepository ticketMessageRepository;

    @Autowired
    private KeyValueRepository keyValueRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    /** The one activation path shared with the Cashfree webhook — see that class. */
    @Autowired
    private SubscriptionActivationService subscriptionActivationService;

    public PaginatedResultResponse getAllUsersForAdmin(int page, int size, String userType, String userStatus) {
        return getAllUsersForAdmin(page, size, userType, userStatus, null, null);
    }

    /**
     * @param plan   exact subscription_plans.title of the member's LATEST subscription, or null
     * @param search matched with LIKE against first name, last name, "first last", e-mail,
     *               mobile and memberId — across ALL members, not the current page. The admin
     *               list used to filter the 10 rows it already had, so a member on page 7 could
     *               not be found by name at all (client, 2026-09-27). Blank → no filter.
     */
    public PaginatedResultResponse getAllUsersForAdmin(int page, int size, String userType, String userStatus,
                                                       String plan, String search) {
        String s = search == null || search.trim().isEmpty() ? null : search.trim();
        String p = plan == null || plan.trim().isEmpty() ? null : plan.trim();
        PaginatedResultResponse response = new PaginatedResultResponse();
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<Object[]> userPage = userRepository.findAllUsersForAdmin(userType, userStatus, p, s, pageable);

            List<Map<String, Object>> userList = new ArrayList<>();
            for (Object[] row : userPage.getContent()) {
                Map<String, Object> dto = new HashMap<>();
                dto.put("userId", row[0] != null ? ((Number) row[0]).longValue() : null);
                dto.put("username", row[1] != null ? (String) row[1] : null);
                dto.put("profileId", row[2] != null ? (String) row[2] : null);
                dto.put("location", row[3] != null ? (String) row[3] : null);
                dto.put("casteName", row[4] != null ? (String) row[4] : null);
                dto.put("profileCompletedPercentage", row[5] != null ? ((Number) row[5]).intValue() : 0);
                dto.put("subscriptionPlan", row[6] != null ? (String) row[6] : "Free");
                dto.put("approvalStatus", row[7] != null ? row[7].toString() : null);
                dto.put("createdOn", row[8] != null ? (Date) row[8] : null);
                dto.put("isActive", row[9] != null ? row[9].toString() : null);
                dto.put("isUser", row.length > 10 && row[10] != null ? row[10].toString() : "FA");
                dto.put("resubmissionNote", row.length > 11 && row[11] != null ? (String) row[11] : null);
                userList.add(dto);
            }

            PaginationData paginationData = new PaginationData();
            paginationData.setTotalPages(userPage.getTotalPages());
            paginationData.setTotalElements(userPage.getTotalElements());
            paginationData.setCurrentPage(page);
            paginationData.setPageSize(size);

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Users fetched successfully");
            response.setData(userList);
            response.setPaginationData(paginationData);

        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching users: " + e.getMessage());
        }
        return response;
    }

    public ResultResponse toggleUserActiveStatus(Long userId) {
        ResultResponse response = new ResultResponse();
        try {
            Optional<UserEntity> optionalUser = userRepository.findByUserId(userId);
            if (optionalUser.isEmpty()) {
                response.setCode(404);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("User not found with ID: " + userId);
                return response;
            }

            UserEntity user = optionalUser.get();
            ActiveStatus newStatus = user.getIsActive() == ActiveStatus.Y ? ActiveStatus.N : ActiveStatus.Y;
            user.setIsActive(newStatus);
            userRepository.save(user);

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("User active status changed to " + newStatus);
            response.setData(newStatus);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error toggling user status: " + e.getMessage());
        }
        return response;
    }

    public ResultResponse getUserDetailForAdmin(Long userId) {
        ResultResponse response = new ResultResponse();
        try {
            List<Object[]> result = userRepository.findUserDetailForAdmin(userId);
            if (result.isEmpty()) {
                response.setCode(404);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("User not found with ID: " + userId);
                return response;
            }

            Object[] row = result.get(0);
            AdminUserDetailDTO dto = new AdminUserDetailDTO();
            dto.setUserId(row[0] != null ? ((Number) row[0]).longValue() : null);
            dto.setFirstName(row[1] != null ? (String) row[1] : null);
            dto.setLastName(row[2] != null ? (String) row[2] : null);
            dto.setGender(row[3] != null ? row[3].toString() : null);
            dto.setDob(row[4] != null ? ((java.sql.Date) row[4]).toLocalDate() : null);
            dto.setAge(row[5] != null ? (String) row[5] : null);
            dto.setWeight(row[6] != null ? (String) row[6] : null);
            dto.setFatherName(row[7] != null ? (String) row[7] : null);
            dto.setMotherName(row[8] != null ? (String) row[8] : null);
            dto.setMotherLanguage(row[9] != null ? (String) row[9] : null);
            dto.setProfileCreatedFor(row[10] != null ? (String) row[10] : null);
            dto.setUserStatus(row[11] != null ? row[11].toString() : null);
            dto.setEmail(row[12] != null ? (String) row[12] : null);
            dto.setMobile(row[13] != null ? (String) row[13] : null);
            dto.setLocation(row[14] != null ? (String) row[14] : null);
            dto.setPlaceOfBirth(row[15] != null ? (String) row[15] : null);
            dto.setPin(row[16] != null ? (String) row[16] : null);
            dto.setCasteId(row[17] != null ? ((Number) row[17]).intValue() : null);
            dto.setCasteName(row[18] != null ? (String) row[18] : null);
            dto.setStar(row[19] != null ? (String) row[19] : null);
            dto.setDosham(row[20] != null ? (String) row[20] : null);
            dto.setDegree(row[21] != null ? (String) row[21] : null);
            dto.setEducationInDetail(row[22] != null ? (String) row[22] : null);
            dto.setEmployedAt(row[23] != null ? row[23].toString() : null);
            dto.setOccupation(row[24] != null ? (String) row[24] : null);
            dto.setJobPlace(row[25] != null ? (String) row[25] : null);
            dto.setAnnualIncome(row[26] != null ? (String) row[26] : null);
            dto.setProfileImage(row[27] != null ? (String) row[27] : null);
            dto.setHoroscope(row[28] != null ? (String) row[28] : null);
            // Appended to the END of the select on purpose — these reads are POSITIONAL, so a
            // column added in the middle silently shifts every mapping after it.
            dto.setHobbies(row.length > 29 && row[29] != null ? (String) row[29] : null);
            dto.setAbout(row.length > 30 && row[30] != null ? (String) row[30] : null);
            dto.setHeight(row.length > 31 && row[31] != null ? (String) row[31] : null);
            dto.setBirthTime(str(row, 32));
            dto.setMoonSign(str(row, 33));
            dto.setPhysicalStatus(str(row, 34));
            dto.setMaritalStatus(str(row, 35));
            dto.setCurrentAddress(str(row, 36));
            dto.setNativePlace(str(row, 37));
            dto.setFamilyType(str(row, 38));
            dto.setFamilyStatus(str(row, 39));
            dto.setFatherOccupation(str(row, 40));
            dto.setMotherOccupation(str(row, 41));
            dto.setNoOfBrothers(str(row, 42));
            dto.setBrothersMarried(str(row, 43));
            dto.setNoOfSisters(str(row, 44));
            dto.setSistersMarried(str(row, 45));
            dto.setSubcasteId(row.length > 46 && row[46] != null ? ((Number) row[46]).intValue() : null);
            dto.setSubcasteName(str(row, 47));

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("User detail fetched successfully");
            response.setData(dto);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching user detail: " + e.getMessage());
        }
        return response;
    }

    @Transactional
    public ResultResponse createUserFromAdmin(AdminUserDetailDTO request) {
        ResultResponse response = new ResultResponse();
        try {
            // Check if mobile already exists
            if (request.getMobile() != null) {
                UserEntity existingUser = userRepository.findByMobile(request.getMobile());
                if (existingUser != null) {
                    response.setCode(409);
                    response.setStatus(ResponseStatus.FAILURE);
                    response.setMessage("User already exists with mobile: " + request.getMobile());
                    return response;
                }
            }

            // Email must be unique too. Only the mobile was checked, so staff could create a
            // second account on an address that already has one — the same hole that produced
            // duplicate accounts through self-registration and was closed there on 2026-09-05.
            // findByEmail returns the NEWEST match, so a duplicate also breaks login for the
            // older account: it can never be reached by e-mail again.
            if (request.getEmail() != null && !request.getEmail().isBlank()) {
                UserEntity byEmail = userRepository.findByEmail(request.getEmail().trim());
                if (byEmail != null) {
                    response.setCode(409);
                    response.setStatus(ResponseStatus.FAILURE);
                    response.setMessage("User already exists with email: " + request.getEmail());
                    return response;
                }
            }

            // === Save UserEntity ===
            UserEntity user = new UserEntity();
            user.setFirstName(request.getFirstName());
            user.setLastName(request.getLastName());
            user.setGender(Gender.valueOf(request.getGender()));
            user.setDob(request.getDob());
            user.setAge(request.getAge());
            user.setEmail(request.getEmail());
            user.setMobile(request.getMobile());
            user.setLocation(request.getLocation());
            user.setPin(request.getPin());
            user.setCasteId(request.getCasteId());
            user.setSubcasteId(request.getSubcasteId() == null ? null : request.getSubcasteId().longValue());
            user.setProfileCreated(request.getProfileCreatedFor());
            user.setProfileImage(request.getProfileImage());
            user.setIsBlocked('N');
            user.setIsUser(IsUser.FA);
            user.setViewCount(0);

            // Set approval status
            if (request.getUserStatus() != null) {
                user.setUserStatus(ApprovalStatus.valueOf(request.getUserStatus()));
            } else {
                user.setUserStatus(ApprovalStatus.PENDING);
            }

            // Generate memberId
            String newMemberId = generateMemberId();
            user.setMemberId(newMemberId);

            userRepository.save(user);

            // === Save UserDetailEntity ===
            UserDetailEntity userDetail = new UserDetailEntity();
            userDetail.setUserId(user.getUserId());
            userDetail.setWeight(request.getWeight());
            userDetail.setDegree(request.getDegree());
            userDetail.setEducationInDetail(request.getEducationInDetail());
            userDetail.setOccupation(request.getOccupation());
            userDetail.setJobPlace(request.getJobPlace());
            userDetail.setAnnualIncome(request.getAnnualIncome());
            userDetail.setHoroscope(request.getHoroscope());
            // These were missing on CREATE only — the update path had them, so a member added
            // here lost their height, about, interests and both addresses until someone
            // re-saved them from the edit screen.
            userDetail.setHeight(request.getHeight());
            userDetail.setAbout(request.getAbout());
            userDetail.setHobbies(request.getHobbies());
            userDetail.setPresentAddress(request.getCurrentAddress());
            userDetail.setPermanentAddress(request.getNativePlace());

            // EmployedAt
            if (request.getEmployedAt() != null && !request.getEmployedAt().isEmpty()) {
                userDetail.setEmployedAt(mapEmploymentType(request.getEmployedAt()));
            }

            ObjectMapper mapper = new ObjectMapper();

            // Family Info JSON — see writeFamilyInfo.
            if (hasAnyFamilyField(request)) {
                userDetail.setFamilyInfo(writeFamilyInfo(mapper, userDetail.getFamilyInfo(), request));
            }

            // Basic Info JSON
            Map<String, String> basicInfoMap = new HashMap<>();
            basicInfoMap.put("place_of_birth", nullToEmpty(request.getPlaceOfBirth()));
            basicInfoMap.put("mother_language", nullToEmpty(request.getMotherLanguage()));
            basicInfoMap.put("marital_status", nullToEmpty(request.getMaritalStatus()));
            basicInfoMap.put("physical_status", nullToEmpty(request.getPhysicalStatus()));
            userDetail.setBasicInfo(mapper.writeValueAsString(basicInfoMap));

            // Astro Info JSON (as List of Map, matching existing pattern)
            Map<String, String> astroInfoMap = new HashMap<>();
            astroInfoMap.put("star", nullToEmpty(request.getStar()));
            astroInfoMap.put("dosham", nullToEmpty(request.getDosham()));
            astroInfoMap.put("moon_sign", nullToEmpty(request.getMoonSign()));
            astroInfoMap.put("birth_time", nullToEmpty(request.getBirthTime()));
            List<Map<String, String>> astroInfoList = new ArrayList<>();
            astroInfoList.add(astroInfoMap);
            userDetail.setAstronomicInfo(mapper.writeValueAsString(astroInfoList));

            // Languages JSON
            if (request.getMotherLanguage() != null && !request.getMotherLanguage().isEmpty()) {
                List<String> languages = new ArrayList<>();
                languages.add(request.getMotherLanguage());
                userDetail.setLanguages(mapper.writeValueAsString(languages));
            }

            userDetailRepository.save(userDetail);

            // Auto-assign the Free plan, exactly as self-registration does. Without this row a
            // member created here has NO subscription at all: a Profile Boost cannot be
            // credited to them ("no ACTIVE subscription to credit"), and any report counting
            // members by plan misses them entirely. Best-effort — a failure here must not
            // undo an otherwise valid member.
            try {
                UserSubscriptions freeSub = new UserSubscriptions();
                freeSub.setUserId(user.getUserId());
                freeSub.setSubscriptionPlanId(1L);
                freeSub.setStatus(SubscriptionStatus.ACTIVE);
                freeSub.setStartDate(java.time.LocalDate.now());
                freeSub.setAutoRenew(com.uravugal.matrimony.enums.ActiveStatus.N);
                userSubscriptionsRepository.save(freeSub);
            } catch (Exception subEx) {
                System.err.println("Admin-created user " + user.getUserId()
                        + " has no Free subscription row: " + subEx.getMessage());
            }

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("User created successfully");
            response.setData(Map.of("userId", user.getUserId(), "memberId", newMemberId));
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error creating user: " + e.getMessage());
        }
        return response;
    }

    @Transactional
    /**
     * Rebuilds `user_details.familyInfo`, preserving everything already there.
     *
     * 🔴 The old code read it with `readValue(json, Map.class)` and wrote a plain OBJECT. The
     * column holds a JSON ARRAY ([{...}]) written by the app, so the read always threw (silently
     * caught) and the write REPLACED the whole array with {fatherName, father} — destroying
     * family type, status, both occupations, every sibling count and the app-only keys (house,
     * brother_sister). 10 production rows were already OBJECT-shaped from this.
     *
     * ⚠ There were TWO copies of that logic, create and update, and fixing one left the other
     * destroying data. It lives here now so that cannot happen again.
     */
    private String writeFamilyInfo(ObjectMapper mapper, String existingJson, AdminUserDetailDTO request)
            throws Exception {
        Map<String, Object> family = new HashMap<>();
        if (existingJson != null && !existingJson.isBlank()) {
            try {
                Object parsed = mapper.readValue(existingJson, Object.class);
                if (parsed instanceof List<?> list && !list.isEmpty()
                        && list.get(0) instanceof Map<?, ?> first) {
                    first.forEach((k, v) -> family.put(String.valueOf(k), v));
                } else if (parsed instanceof Map<?, ?> obj) {
                    obj.forEach((k, v) -> family.put(String.valueOf(k), v));
                }
            } catch (Exception ignored) {
            }
        }
        // The app's own key names — anything else is invisible to it.
        putIfPresent(family, "father", request.getFatherName());
        putIfPresent(family, "mother", request.getMotherName());
        putIfPresent(family, "father_occupation", request.getFatherOccupation());
        putIfPresent(family, "mother_occupation", request.getMotherOccupation());
        putIfPresent(family, "family_type", request.getFamilyType());
        putIfPresent(family, "family_status", request.getFamilyStatus());
        putIfPresent(family, "no_of_brother", request.getNoOfBrothers());
        putIfPresent(family, "brother_married", request.getBrothersMarried());
        putIfPresent(family, "no_of_sister", request.getNoOfSisters());
        putIfPresent(family, "sister_married", request.getSistersMarried());
        // The app derives this rather than asking for it; keep the two in step.
        try {
            int b = Integer.parseInt(String.valueOf(family.getOrDefault("no_of_brother", "0")).trim());
            int g = Integer.parseInt(String.valueOf(family.getOrDefault("no_of_sister", "0")).trim());
            family.put("no_of_siblings", String.valueOf(b + g));
        } catch (Exception ignored) {
        }
        List<Map<String, Object>> familyList = new ArrayList<>();
        familyList.add(family);
        return mapper.writeValueAsString(familyList);
    }

    private static boolean hasAnyFamilyField(AdminUserDetailDTO r) {
        return r.getFatherName() != null || r.getMotherName() != null
                || r.getFatherOccupation() != null || r.getMotherOccupation() != null
                || r.getFamilyType() != null || r.getFamilyStatus() != null
                || r.getNoOfBrothers() != null || r.getBrothersMarried() != null
                || r.getNoOfSisters() != null || r.getSistersMarried() != null;
    }

    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null) map.put(key, value);
    }

    /** Positional read from a native-query row, tolerant of a shorter row from an older jar. */
    private static String str(Object[] row, int i) {
        return row.length > i && row[i] != null ? row[i].toString() : null;
    }

    /**
     * The "your profile is approved" message on every channel the member can have (2026-10-20):
     * email (when the account has one), WhatsApp template, push and an in-app row. Each channel
     * is independent and best-effort — one failing never blocks the others or the approval.
     * Push uses the DIRECT sender: the plan-gated one skips Free/Starter, and every newly
     * approved member is Free, so the approval push used to reach nobody.
     */
    private void notifyProfileApproved(UserEntity user) {
        Long userId = user.getUserId();
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            try { emailService.sendApprovalEmail(user.getEmail(), fullName(user)); }
            catch (Exception e) { System.out.println("Warning: approval email failed: " + e.getMessage()); }
        }
        try { whatsAppService.profileApproved(user); }
        catch (Exception e) { System.out.println("Warning: approval WhatsApp failed: " + e.getMessage()); }
        try {
            pushNotificationService.sendPushNotificationToUserDirect(userId, "Profile Approved! 🎉",
                    "Your profile has been approved. You can now explore matches and connect!");
        } catch (Exception e) { System.out.println("Warning: approval push failed: " + e.getMessage()); }
        try {
            Notification notif = new Notification();
            notif.setReceiverId(userId);
            notif.setTitle("Profile Approved");
            notif.setMessage("Your profile has been approved. You can now explore matches and connect!");
            notif.setNotificationCategory("PROFILE_APPROVED");
            notif.setIsRead(ActiveStatus.N);
            notificationRepository.save(notif);
        } catch (Exception e) { System.out.println("Warning: approval in-app notification failed: " + e.getMessage()); }
    }

    public ResultResponse updateUserFromAdmin(Long userId, AdminUserDetailDTO request) {
        ResultResponse response = new ResultResponse();
        try {
            // Fetch existing user
            Optional<UserEntity> optionalUser = userRepository.findByUserId(userId);
            if (optionalUser.isEmpty()) {
                response.setCode(404);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("User not found with ID: " + userId);
                return response;
            }

            UserEntity user = optionalUser.get();

            // === Update UserEntity fields ===
            if (request.getFirstName() != null)
                user.setFirstName(request.getFirstName());
            if (request.getLastName() != null)
                user.setLastName(request.getLastName());
            if (request.getGender() != null)
                user.setGender(Gender.valueOf(request.getGender()));
            if (request.getDob() != null)
                user.setDob(request.getDob());
            if (request.getAge() != null)
                user.setAge(request.getAge());
            if (request.getEmail() != null)
                user.setEmail(request.getEmail());
            if (request.getMobile() != null)
                user.setMobile(request.getMobile());
            if (request.getLocation() != null)
                user.setLocation(request.getLocation());
            if (request.getPin() != null)
                user.setPin(request.getPin());
            if (request.getCasteId() != null)
                user.setCasteId(request.getCasteId());
            // Subcaste was readable but not writable, so a wrong one could never be corrected.
            if (request.getSubcasteId() != null)
                user.setSubcasteId(request.getSubcasteId() == null ? null : request.getSubcasteId().longValue());
            if (request.getProfileCreatedFor() != null)
                user.setProfileCreated(request.getProfileCreatedFor());
            if (request.getProfileImage() != null)
                user.setProfileImage(request.getProfileImage());
            boolean becameApproved = false;
            if (request.getUserStatus() != null) {
                ApprovalStatus next = ApprovalStatus.valueOf(request.getUserStatus());
                becameApproved = next == ApprovalStatus.APPROVED && user.getUserStatus() != ApprovalStatus.APPROVED;
                user.setUserStatus(next);
                if (becameApproved) { user.setRejectionReason(null); user.setResubmissionNote(null); }
            }

            userRepository.save(user);
            // Approving from the edit page used to be silent — same message as the Approve button.
            if (becameApproved) notifyProfileApproved(user);

            // === Update UserDetailEntity ===
            UserDetailEntity userDetail = userDetailRepository.findByUserId(userId);
            if (userDetail == null) {
                userDetail = new UserDetailEntity();
                userDetail.setUserId(userId);
            }

            if (request.getWeight() != null)
                userDetail.setWeight(request.getWeight());
            if (request.getDegree() != null)
                userDetail.setDegree(request.getDegree());
            if (request.getEducationInDetail() != null)
                userDetail.setEducationInDetail(request.getEducationInDetail());
            if (request.getOccupation() != null)
                userDetail.setOccupation(request.getOccupation());
            if (request.getJobPlace() != null)
                userDetail.setJobPlace(request.getJobPlace());
            if (request.getAnnualIncome() != null)
                userDetail.setAnnualIncome(request.getAnnualIncome());
            if (request.getHoroscope() != null)
                userDetail.setHoroscope(request.getHoroscope());
            if (request.getHobbies() != null)
                userDetail.setHobbies(request.getHobbies());
            if (request.getAbout() != null)
                userDetail.setAbout(request.getAbout());
            if (request.getHeight() != null)
                userDetail.setHeight(request.getHeight());
            if (request.getCurrentAddress() != null)
                userDetail.setPresentAddress(request.getCurrentAddress());
            // "Native Place" is the permanentAddress column — see the app's CLAUDE.md §17.
            if (request.getNativePlace() != null)
                userDetail.setPermanentAddress(request.getNativePlace());

            if (request.getEmployedAt() != null && !request.getEmployedAt().isEmpty()) {
                userDetail.setEmployedAt(mapEmploymentType(request.getEmployedAt()));
            }

            ObjectMapper mapper = new ObjectMapper();

            // Family Info JSON — see writeFamilyInfo for why this cannot be a Map.
            if (hasAnyFamilyField(request)) {
                userDetail.setFamilyInfo(writeFamilyInfo(mapper, userDetail.getFamilyInfo(), request));
            }

            // Basic Info JSON
            if (request.getPlaceOfBirth() != null || request.getMotherLanguage() != null
                    || request.getPhysicalStatus() != null || request.getMaritalStatus() != null) {
                Map<String, String> basicInfoMap = new HashMap<>();
                if (userDetail.getBasicInfo() != null) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, String> existing = mapper.readValue(userDetail.getBasicInfo(), Map.class);
                        basicInfoMap.putAll(existing);
                    } catch (Exception ignored) {
                    }
                }
                // ⚠ snake_case: these are the keys the APP reads. The admin GET used to look
                // for "$.placeOfBirth", which never matched what is written here.
                if (request.getPlaceOfBirth() != null)
                    basicInfoMap.put("place_of_birth", request.getPlaceOfBirth());
                if (request.getMotherLanguage() != null)
                    basicInfoMap.put("mother_language", request.getMotherLanguage());
                if (request.getPhysicalStatus() != null)
                    basicInfoMap.put("physical_status", request.getPhysicalStatus());
                if (request.getMaritalStatus() != null)
                    basicInfoMap.put("marital_status", request.getMaritalStatus());
                userDetail.setBasicInfo(mapper.writeValueAsString(basicInfoMap));
            }

            // Astro Info JSON
            if (request.getStar() != null || request.getDosham() != null
                    || request.getBirthTime() != null || request.getMoonSign() != null) {
                Map<String, String> astroInfoMap = new HashMap<>();
                // Try to preserve existing astro data
                if (userDetail.getAstronomicInfo() != null) {
                    try {
                        @SuppressWarnings("unchecked")
                        List<Map<String, String>> existingList = mapper.readValue(userDetail.getAstronomicInfo(),
                                List.class);
                        if (!existingList.isEmpty())
                            astroInfoMap.putAll(existingList.get(0));
                    } catch (Exception ignored) {
                    }
                }
                if (request.getStar() != null)
                    astroInfoMap.put("star", request.getStar());
                if (request.getDosham() != null)
                    astroInfoMap.put("dosham", request.getDosham());
                if (request.getBirthTime() != null)
                    astroInfoMap.put("birth_time", request.getBirthTime());
                if (request.getMoonSign() != null)
                    astroInfoMap.put("moon_sign", request.getMoonSign());
                List<Map<String, String>> astroInfoList = new ArrayList<>();
                astroInfoList.add(astroInfoMap);
                userDetail.setAstronomicInfo(mapper.writeValueAsString(astroInfoList));
            }

            // Languages JSON
            if (request.getMotherLanguage() != null && !request.getMotherLanguage().isEmpty()) {
                List<String> languages = new ArrayList<>();
                languages.add(request.getMotherLanguage());
                userDetail.setLanguages(mapper.writeValueAsString(languages));
            }

            userDetailRepository.save(userDetail);

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("User updated successfully");
            response.setData(Map.of("userId", userId));
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error updating user: " + e.getMessage());
        }
        return response;
    }

    private String generateMemberId() {
        String lastId = userRepository.findLastMemberId();
        int number = 0;
        if (lastId != null && lastId.startsWith(PREFIX)) {
            number = Integer.parseInt(lastId.substring(PREFIX.length()));
        }
        number++;
        return PREFIX + String.format("%06d", number);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private EmploymentType mapEmploymentType(String value) {
        if (value == null)
            return null;
        return switch (value.toUpperCase()) {
            case "PRIVATE" -> EmploymentType.PRIVATE;
            case "GOVERNMENT", "GOVT" -> EmploymentType.GOVT;
            case "SELF_EMPLOYMENT", "SELF" -> EmploymentType.SELF;
            case "NO_JOB", "NOJOB" -> EmploymentType.NOJOB;
            case "UNEMPLOYED" -> EmploymentType.UNEMPLOYED;
            default -> EmploymentType.valueOf(value.toUpperCase());
        };
    }

    @Transactional
    public ResultResponse approvePayment(AdminApprovePaymentRequestDTO request) {
        ResultResponse response = new ResultResponse();
        try {
            Optional<PaymentRequestEntity> optPaymentReq = paymentRequestRepository
                    .findById(request.getPaymentRequestId());
            if (optPaymentReq.isEmpty()) {
                response.setCode(404);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("Payment Request not found with ID: " + request.getPaymentRequestId());
                return response;
            }

            PaymentRequestEntity paymentReq = optPaymentReq.get();
            if (paymentReq.getStatus() != PaymentRequestStatus.PENDING) {
                response.setCode(400);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("Payment Request is not in PENDING status.");
                return response;
            }

            paymentReq.setStatus(PaymentRequestStatus.APPROVED);
            paymentReq.setVerifiedAt(LocalDateTime.now());
            paymentReq.setVerifiedBy(request.getAdminId());
            paymentRequestRepository.save(paymentReq);

            // BOOST_PURCHASE: increment boost_credits instead of creating a subscription
            if ("BOOST_PURCHASE".equals(paymentReq.getNote())) {
                subscriptionActivationService.addBoostCredit(paymentReq.getUserId());
                response.setCode(200);
                response.setStatus(ResponseStatus.SUCCESS);
                response.setMessage("Boost purchase approved — 1 credit added");
                return response;
            }

            Optional<SubscriptionPlan> optPlan = subscriptionPlanRepository.findById(paymentReq.getPlanId());
            if (optPlan.isEmpty()) {
                response.setCode(404);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("Subscription Plan not found with ID: " + paymentReq.getPlanId());
                return response;
            }

            SubscriptionPlan plan = optPlan.get();

            // A plan that has been retired (isActive = 'N') must not be sold to anyone new, even
            // if a payment request for it was raised before it was pulled. Existing subscribers on
            // that plan are unaffected — entitlements are read from planFeatures by plan id, not
            // from this flag — so grandfathering keeps working.
            if (plan.getIsActive() != ActiveStatus.Y) {
                response.setCode(400);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("SUBSCRIPTION_PLAN_INACTIVE: '" + plan.getTitle()
                        + "' is no longer offered. Ask the member to choose a currently available plan.");
                return response;
            }

            UserEntity member = userRepository.findById(paymentReq.getUserId()).orElse(null);
            if (member == null) {
                response.setCode(404);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("User not found with ID: " + paymentReq.getUserId());
                return response;
            }

            // Shared activation (subscription row + push + in-app + email) — same code the
            // Cashfree webhook and assignPlanToUser run, so all three stay identical.
            subscriptionActivationService.activatePlan(member, plan,
                    "PAY_REQ_" + paymentReq.getId(), "PAYMENT_APPROVED");

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Payment approved and subscription created successfully");
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error approving payment: " + e.getMessage());
        }
        return response;
    }

    // ==================== DASHBOARD STATS ====================

    public ResultResponse getDashboardStats() {
        ResultResponse response = new ResultResponse();
        try {
            long totalUsers = userRepository.count();
            long activeUsers = userRepository.findAllByIsActive(ActiveStatus.Y).size();
            long blockedUsers = userRepository.findAll().stream().filter(u -> u.getIsBlocked() == 'Y').count();
            long inactiveUsers = totalUsers - activeUsers;
            long pendingApprovals = userRepository.findAll().stream()
                .filter(u -> u.getUserStatus() == ApprovalStatus.PENDING).count();

            // Payment stats
            List<PaymentRequestEntity> allPayments = paymentRequestRepository.findAll();
            BigDecimal totalRevenue = allPayments.stream()
                .filter(p -> p.getStatus() == PaymentRequestStatus.APPROVED)
                .map(PaymentRequestEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            // Online (Cashfree) payments live in payment_orders, not PAYMENT_REQUESTS.
            Object cfPaid = entityManager.createNativeQuery(
                "SELECT COALESCE(SUM(amount),0) FROM payment_orders WHERE orderStatus = 'PAID'").getSingleResult();
            totalRevenue = totalRevenue.add(toDecimal(cfPaid));

            // Today's registrations
            long todayRegistrations = userRepository.findAll().stream()
                .filter(u -> u.getCreatedAt() != null &&
                    u.getCreatedAt().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate().equals(LocalDate.now()))
                .count();

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalUsers", totalUsers);
            stats.put("activeUsers", activeUsers);
            stats.put("inactiveUsers", inactiveUsers);
            stats.put("blockedUsers", blockedUsers);
            stats.put("pendingApprovals", pendingApprovals);
            stats.put("totalRevenue", totalRevenue);
            stats.put("todayRegistrations", todayRegistrations);
            stats.put("pendingPayments", allPayments.stream().filter(p -> p.getStatus() == PaymentRequestStatus.PENDING).count());

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Dashboard stats fetched successfully");
            response.setData(stats);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching dashboard stats: " + e.getMessage());
        }
        return response;
    }

    // ==================== ACTIVITY OVERVIEW (dashboard) ====================

    /** IST calendar day of a column that holds UTC digits (GenericEntity / LocalDateTime.now() on a UTC JVM). */
    private static final String IST_DAY = "DATE(CONVERT_TZ(%s,'+00:00','+05:30'))";

    private static BigDecimal toDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal) return (BigDecimal) o;
        return new BigDecimal(o.toString());
    }

    private static long toLong(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }

    /**
     * Daily rows for one metric over [from, to], plus the window of the same
     * length right before it (so the page can say "vs previous period").
     * {@code dayExpr} must yield an IST calendar date; the query is
     * {@code SELECT dayExpr, COUNT(*)[, SUM(x)] FROM table WHERE extra GROUP BY day}.
     */
    private void series(Map<String, Map<String, Object>> daily, Map<String, Object> totals, Map<String, Object> previous,
                        String key, String dayExpr, String table, String extraWhere, String sumExpr,
                        LocalDate from, LocalDate to, LocalDate prevFrom) {
        String sql = "SELECT " + dayExpr + " AS d, COUNT(*)" + (sumExpr != null ? ", COALESCE(SUM(" + sumExpr + "),0)" : "")
            + " FROM " + table + " WHERE " + dayExpr + " BETWEEN :f AND :t"
            + (extraWhere != null ? " AND " + extraWhere : "") + " GROUP BY d";
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(sql)
            .setParameter("f", java.sql.Date.valueOf(prevFrom))
            .setParameter("t", java.sql.Date.valueOf(to))
            .getResultList();
        long cur = 0, prev = 0;
        BigDecimal curSum = BigDecimal.ZERO, prevSum = BigDecimal.ZERO;
        for (Object[] r : rows) {
            String day = String.valueOf(r[0]);
            long c = toLong(r[1]);
            BigDecimal sum = sumExpr != null ? toDecimal(r[2]) : null;
            Map<String, Object> d = daily.get(day);
            if (d != null) {
                d.put(key, c);
                if (sum != null) d.put(key + "Amount", sum);
                cur += c; if (sum != null) curSum = curSum.add(sum);
            } else if (LocalDate.parse(day).isBefore(from)) {
                prev += c; if (sum != null) prevSum = prevSum.add(sum);
            }
        }
        totals.put(key, cur);
        previous.put(key, prev);
        if (sumExpr != null) {
            totals.put(key + "Amount", curSum);
            previous.put(key + "Amount", prevSum);
        }
    }

    private long count(String sql) {
        return toLong(entityManager.createNativeQuery(sql).getSingleResult());
    }

    /**
     * Everything the admin dashboard shows for an IST date range: per-day
     * activity (registrations, interests, chat, views, likes, shortlists,
     * payments, support load), totals with the previous window for deltas,
     * and a live snapshot of the member base and the queues that need action.
     */
    public ResultResponse getActivityOverview(LocalDate from, LocalDate to) {
        ResultResponse response = new ResultResponse();
        try {
            if (to.isBefore(from)) { LocalDate x = from; from = to; to = x; }
            long span = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;
            if (span > 370) { from = to.minusDays(369); span = 370; }
            LocalDate prevFrom = from.minusDays(span);

            Map<String, Map<String, Object>> daily = new java.util.LinkedHashMap<>();
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("day", d.toString());
                daily.put(d.toString(), row);
            }
            Map<String, Object> totals = new java.util.LinkedHashMap<>();
            Map<String, Object> previous = new java.util.LinkedHashMap<>();

            String usersDay = String.format(IST_DAY, "createdAt");
            series(daily, totals, previous, "registrations", usersDay, "users", "COALESCE(isUser,'') <> 'ADM'", null, from, to, prevFrom);
            series(daily, totals, previous, "registrationsMale", usersDay, "users", "COALESCE(isUser,'') <> 'ADM' AND gender = 'M'", null, from, to, prevFrom);
            series(daily, totals, previous, "registrationsFemale", usersDay, "users", "COALESCE(isUser,'') <> 'ADM' AND gender = 'F'", null, from, to, prevFrom);
            series(daily, totals, previous, "interestsSent", String.format(IST_DAY, "createdAt"), "interestRequest", null, null, from, to, prevFrom);
            series(daily, totals, previous, "interestsAccepted", String.format(IST_DAY, "updatedAt"), "interestRequest", "acceptStatus = 'APPROVED'", null, from, to, prevFrom);
            series(daily, totals, previous, "messages", String.format(IST_DAY, "createdAt"), "chats", null, null, from, to, prevFrom);
            series(daily, totals, previous, "profileViews", String.format(IST_DAY, "createdAt"), "viewedProfile", null, null, from, to, prevFrom);
            series(daily, totals, previous, "likes", String.format(IST_DAY, "createdAt"), "userLikes", null, null, from, to, prevFrom);
            series(daily, totals, previous, "shortlists", String.format(IST_DAY, "createdAt"), "shortlisted_profiles", null, null, from, to, prevFrom);
            // payment_orders timestamps are written as IST wall-clock (CashfreeService.istNow()), so no conversion.
            series(daily, totals, previous, "onlinePayments", "DATE(fulfilledAt)", "payment_orders", "orderStatus = 'PAID' AND fulfilledAt IS NOT NULL", "amount", from, to, prevFrom);
            series(daily, totals, previous, "manualPayments", String.format(IST_DAY, "COALESCE(verified_at, created_at)"), "PAYMENT_REQUESTS", "status = 'APPROVED'", "amount", from, to, prevFrom);
            series(daily, totals, previous, "paidPlans", String.format(IST_DAY, "createdAt"), "user_subscriptions", "subscriptionPlanId <> 1", null, from, to, prevFrom);
            series(daily, totals, previous, "boosts", String.format(IST_DAY, "createdAt"), "profile_boosts", null, null, from, to, prevFrom);
            series(daily, totals, previous, "reports", String.format(IST_DAY, "reportedAt"), "userReports", null, null, from, to, prevFrom);
            series(daily, totals, previous, "supportTickets", String.format(IST_DAY, "created_at"), "support_tickets", null, null, from, to, prevFrom);
            series(daily, totals, previous, "callbacks", String.format(IST_DAY, "createdAt"), "callback_requests", null, null, from, to, prevFrom);
            series(daily, totals, previous, "serviceRequests", String.format(IST_DAY, "createdAt"), "service_requests", null, null, from, to, prevFrom);

            // Derived: payments & revenue = online + manual
            for (Map<String, Object> row : daily.values()) {
                for (String k : new String[]{"registrations","registrationsMale","registrationsFemale","interestsSent","interestsAccepted","messages",
                        "profileViews","likes","shortlists","onlinePayments","manualPayments","paidPlans","boosts","reports","supportTickets","callbacks","serviceRequests"}) {
                    row.putIfAbsent(k, 0L);
                }
                row.putIfAbsent("onlinePaymentsAmount", BigDecimal.ZERO);
                row.putIfAbsent("manualPaymentsAmount", BigDecimal.ZERO);
                row.put("payments", toLong(row.get("onlinePayments")) + toLong(row.get("manualPayments")));
                row.put("revenue", toDecimal(row.get("onlinePaymentsAmount")).add(toDecimal(row.get("manualPaymentsAmount"))));
            }
            for (Map<String, Object> m : List.of(totals, previous)) {
                m.put("payments", toLong(m.get("onlinePayments")) + toLong(m.get("manualPayments")));
                m.put("revenue", toDecimal(m.get("onlinePaymentsAmount")).add(toDecimal(m.get("manualPaymentsAmount"))));
            }

            // Snapshot (right now, not range-bound)
            Map<String, Object> snap = new java.util.LinkedHashMap<>();
            String member = "FROM users WHERE COALESCE(isUser,'') <> 'ADM'";
            snap.put("totalMembers", count("SELECT COUNT(*) " + member));
            snap.put("activeMembers", count("SELECT COUNT(*) " + member + " AND isActive = 'Y'"));
            snap.put("approvedMembers", count("SELECT COUNT(*) " + member + " AND userStatus = 'APPROVED'"));
            snap.put("pendingApprovals", count("SELECT COUNT(*) " + member + " AND userStatus = 'PENDING'"));
            snap.put("rejectedMembers", count("SELECT COUNT(*) " + member + " AND userStatus = 'REJECTED'"));
            snap.put("blockedMembers", count("SELECT COUNT(*) " + member + " AND isBlocked = 'Y'"));
            snap.put("activeMale", count("SELECT COUNT(*) " + member + " AND isActive = 'Y' AND gender = 'M'"));
            snap.put("activeFemale", count("SELECT COUNT(*) " + member + " AND isActive = 'Y' AND gender = 'F'"));
            snap.put("withPhoto", count("SELECT COUNT(*) " + member + " AND isActive = 'Y' AND profileImage IS NOT NULL AND profileImage <> ''"));
            snap.put("onlineNow", count("SELECT COUNT(*) " + member + " AND isOnline = 1"));
            snap.put("pendingManualPayments", count("SELECT COUNT(*) FROM PAYMENT_REQUESTS WHERE status = 'PENDING'"));
            snap.put("openReports", count("SELECT COUNT(*) FROM userReports WHERE COALESCE(status,'PENDING') = 'PENDING'"));
            snap.put("openTickets", count("SELECT COUNT(*) FROM support_tickets WHERE status IN ('OPEN','IN_PROGRESS')"));
            snap.put("newCallbacks", count("SELECT COUNT(*) FROM callback_requests WHERE status = 'NEW'"));
            snap.put("pendingServiceRequests", count("SELECT COUNT(*) FROM service_requests WHERE status = 'PENDING'"));
            snap.put("activeBoosts", count("SELECT COUNT(*) FROM profile_boosts WHERE status = 'ACTIVE' AND expiresAt > CONVERT_TZ(NOW(),'+00:00','+05:30')"));
            snap.put("activeConversations", count("SELECT COUNT(*) FROM conversations WHERE status = 'ACCEPTED'"));
            snap.put("lifetimeRevenue", toDecimal(entityManager.createNativeQuery(
                "SELECT (SELECT COALESCE(SUM(amount),0) FROM payment_orders WHERE orderStatus='PAID') + (SELECT COALESCE(SUM(amount),0) FROM PAYMENT_REQUESTS WHERE status='APPROVED')").getSingleResult()));

            // Plan mix among active members (latest ACTIVE, unexpired row per member)
            @SuppressWarnings("unchecked")
            List<Object[]> mix = entityManager.createNativeQuery(
                "SELECT sp.title, COUNT(DISTINCT us.userId) FROM user_subscriptions us " +
                "JOIN subscription_plans sp ON sp.id = us.subscriptionPlanId " +
                "JOIN users u ON u.userId = us.userId AND u.isActive = 'Y' AND COALESCE(u.isUser,'') <> 'ADM' " +
                "WHERE us.status = 'ACTIVE' AND (us.endDate IS NULL OR us.endDate >= CURDATE()) " +
                "AND us.id = (SELECT us2.id FROM user_subscriptions us2 WHERE us2.userId = us.userId AND us2.status = 'ACTIVE' " +
                "AND (us2.endDate IS NULL OR us2.endDate >= CURDATE()) ORDER BY us2.createdAt DESC, us2.id DESC LIMIT 1) " +
                "GROUP BY sp.title").getResultList();
            Map<String, Long> planMix = new java.util.LinkedHashMap<>();
            long paidTotal = 0;
            for (Object[] r : mix) {
                String title = String.valueOf(r[0]);
                long c = toLong(r[1]);
                if (!"Free".equalsIgnoreCase(title)) paidTotal += c;
                planMix.put(title, c);
            }
            long activeMembers = toLong(snap.get("activeMembers"));
            planMix.put("Free", Math.max(0, activeMembers - paidTotal));
            snap.put("paidMembers", paidTotal);

            Map<String, Object> data = new java.util.LinkedHashMap<>();
            data.put("from", from.toString());
            data.put("to", to.toString());
            data.put("days", span);
            data.put("previousFrom", prevFrom.toString());
            data.put("previousTo", from.minusDays(1).toString());
            data.put("totals", totals);
            data.put("previous", previous);
            data.put("daily", new ArrayList<>(daily.values()));
            data.put("snapshot", snap);
            data.put("planMix", planMix);

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Overview fetched successfully");
            response.setData(data);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching overview: " + e.getMessage());
        }
        return response;
    }

    public ResultResponse getRegistrationTrends() {
        ResultResponse response = new ResultResponse();
        try {
            List<UserEntity> allUsers = userRepository.findAll();
            // Group by month and gender for last 12 months
            Map<String, Map<String, Long>> trends = new java.util.LinkedHashMap<>();
            LocalDate now = LocalDate.now();

            for (int i = 11; i >= 0; i--) {
                LocalDate month = now.minusMonths(i);
                String monthKey = month.getYear() + "-" + String.format("%02d", month.getMonthValue());
                Map<String, Long> genderCounts = new HashMap<>();
                genderCounts.put("male", 0L);
                genderCounts.put("female", 0L);
                trends.put(monthKey, genderCounts);
            }

            for (UserEntity user : allUsers) {
                if (user.getCreatedAt() != null) {
                    LocalDate createdDate = user.getCreatedAt().toInstant()
                        .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                    String monthKey = createdDate.getYear() + "-" + String.format("%02d", createdDate.getMonthValue());
                    if (trends.containsKey(monthKey) && user.getGender() != null) {
                        String genderKey = user.getGender().name().equalsIgnoreCase("M") ? "male" : "female";
                        trends.get(monthKey).merge(genderKey, 1L, Long::sum);
                    }
                }
            }

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Registration trends fetched successfully");
            response.setData(trends);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching registration trends: " + e.getMessage());
        }
        return response;
    }

    public ResultResponse getSubscriptionDistribution() {
        ResultResponse response = new ResultResponse();
        try {
            List<UserSubscriptions> activeSubscriptions = userSubscriptionsRepository.findByStatus(SubscriptionStatus.ACTIVE);
            List<SubscriptionPlan> plans = subscriptionPlanRepository.findAll();

            Map<String, Long> distribution = new HashMap<>();
            for (SubscriptionPlan plan : plans) {
                long count = activeSubscriptions.stream()
                    .filter(s -> s.getSubscriptionPlanId().equals(plan.getId()))
                    .count();
                distribution.put(plan.getTitle(), count);
            }

            // Count free users (no active subscription)
            long totalUsers = userRepository.count();
            long subscribedUsers = activeSubscriptions.stream().map(UserSubscriptions::getUserId).distinct().count();
            distribution.put("Free", totalUsers - subscribedUsers);

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Subscription distribution fetched successfully");
            response.setData(distribution);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching subscription distribution: " + e.getMessage());
        }
        return response;
    }

    // ==================== USER BLOCK/UNBLOCK ====================

    public ResultResponse blockUser(Long userId) {
        ResultResponse response = new ResultResponse();
        try {
            Optional<UserEntity> optUser = userRepository.findById(userId);
            if (optUser.isEmpty()) {
                response.setCode(404);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("User not found");
                return response;
            }
            UserEntity user = optUser.get();
            user.setIsBlocked('Y');
            userRepository.save(user);

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("User blocked successfully");
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error blocking user: " + e.getMessage());
        }
        return response;
    }

    public ResultResponse unblockUser(Long userId) {
        ResultResponse response = new ResultResponse();
        try {
            Optional<UserEntity> optUser = userRepository.findById(userId);
            if (optUser.isEmpty()) {
                response.setCode(404);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("User not found");
                return response;
            }
            UserEntity user = optUser.get();
            user.setIsBlocked('N');
            userRepository.save(user);

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("User unblocked successfully");
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error unblocking user: " + e.getMessage());
        }
        return response;
    }

    // ==================== PROFILE APPROVAL ====================

    public ResultResponse approveUser(Long userId) {
        ResultResponse response = new ResultResponse();
        try {
            Optional<UserEntity> optUser = userRepository.findById(userId);
            if (optUser.isEmpty()) {
                response.setCode(404);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("User not found");
                return response;
            }
            UserEntity user = optUser.get();
            user.setUserStatus(ApprovalStatus.APPROVED);
            user.setRejectionReason(null);
            // The member's resubmission note is consumed once acted on — leaving
            // it would show a stale note on a future rejection round.
            user.setResubmissionNote(null);
            userRepository.save(user);
            notifyProfileApproved(user);

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("User approved successfully");
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error approving user: " + e.getMessage());
        }
        return response;
    }

    /**
     * Admin manually grants a plan to a member (Users -> Assign Plan) — no payment
     * request involved. Mirrors approvePayment's activation exactly (new ACTIVE
     * user_subscriptions row; the latest-row-wins finder makes it take effect
     * immediately), then notifies the member by email + push + in-app.
     *
     * DELIBERATELY allows plans with isActive='N' — the member-facing purchase
     * guard exists to stop SELLING retired plans, while admin assignment is how
     * invisible packs like Trial are granted. The picker in the admin UI lists
     * all plans for exactly this reason.
     */
    /** Deleted-account tombstones (deleted_accounts), newest first. */
    public PaginatedResultResponse getDeletedAccounts(int page, int size) {
        PaginatedResultResponse response = new PaginatedResultResponse();
        try {
            org.springframework.data.domain.Page<com.uravugal.matrimony.models.DeletedAccount> p =
                    deletedAccountRepository.findAll(PageRequest.of(page, size,
                            org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "id")));
            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Deleted accounts fetched");
            response.setData(p.getContent());
            PaginationData pg = new PaginationData();
            pg.setTotalPages(p.getTotalPages());
            pg.setTotalElements(p.getTotalElements());
            pg.setCurrentPage(page);
            pg.setPageSize(size);
            response.setPaginationData(pg);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching deleted accounts: " + e.getMessage());
        }
        return response;
    }

    @Autowired
    private com.uravugal.matrimony.repositories.DeletedAccountRepository deletedAccountRepository;

    // ── Website leads (quick-register form) ─────────────────────────────

    private static final java.util.Set<String> LEAD_STATUSES = java.util.Set.of("NEW", "CONTACTED", "CONVERTED", "DROPPED");

    public PaginatedResultResponse getWebsiteLeads(int page, int size, String status, String search) {
        PaginatedResultResponse response = new PaginatedResultResponse();
        try {
            String st = (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) ? null : status.trim().toUpperCase();
            String q = (search == null || search.isBlank()) ? null : search.trim();
            org.springframework.data.domain.Page<UserEntity> p =
                    userRepository.findWebsiteLeads(st, q, PageRequest.of(page, size));
            java.util.List<Map<String, Object>> rows = new java.util.ArrayList<>();
            for (UserEntity u : p.getContent()) {
                Map<String, Object> r = new HashMap<>();
                r.put("userId", u.getUserId());
                r.put("name", u.getFirstName());
                r.put("mobile", u.getMobile());
                r.put("profileFor", u.getProfileCreated());
                r.put("verified", "Y".equals(u.getLeadVerified()));
                r.put("status", u.getLeadStatus() == null ? "NEW" : u.getLeadStatus());
                r.put("note", u.getLeadNote());
                r.put("createdAt", u.getCreatedAt());
                rows.add(r);
            }
            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Website leads fetched");
            response.setData(rows);
            PaginationData pg = new PaginationData();
            pg.setTotalPages(p.getTotalPages());
            pg.setTotalElements(p.getTotalElements());
            pg.setCurrentPage(page);
            pg.setPageSize(size);
            response.setPaginationData(pg);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching website leads: " + e.getMessage());
        }
        return response;
    }

    /** Admin → Verification → Incomplete Registrations (new app flow, 2026-10-19). */
    public PaginatedResultResponse getIncompleteRegistrations(int page, int size, String search) {
        PaginatedResultResponse response = new PaginatedResultResponse();
        try {
            String q = (search == null || search.isBlank()) ? null : search.trim();
            org.springframework.data.domain.Page<UserEntity> p =
                    userRepository.findIncompleteRegistrations(q, PageRequest.of(page, size));
            ObjectMapper mapper = new ObjectMapper();
            java.util.List<Map<String, Object>> rows = new java.util.ArrayList<>();
            for (UserEntity u : p.getContent()) {
                Map<String, Object> r = new HashMap<>();
                r.put("userId", u.getUserId());
                r.put("name", ((u.getFirstName() == null ? "" : u.getFirstName()) + " " + (u.getLastName() == null ? "" : u.getLastName())).trim());
                r.put("mobile", u.getMobile());
                r.put("profileFor", u.getProfileCreated());
                int done = 0; Map<String, Object> draft = new HashMap<>();
                try {
                    if (u.getRegistrationDraft() != null) draft = mapper.readValue(u.getRegistrationDraft(), Map.class);
                    Object ds = draft.get("doneSteps");
                    if (ds instanceof java.util.List) done = ((java.util.List<?>) ds).size();
                } catch (Exception ignored) { }
                r.put("stepsDone", done);
                r.put("stepsTotal", 4);
                r.put("gender", draft.get("gender"));
                r.put("district", draft.get("district"));
                r.put("createdAt", u.getCreatedAt());
                r.put("updatedAt", u.getUpdatedAt());
                rows.add(r);
            }
            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Incomplete registrations fetched");
            response.setData(rows);
            PaginationData pg = new PaginationData();
            pg.setTotalPages(p.getTotalPages());
            pg.setTotalElements(p.getTotalElements());
            pg.setCurrentPage(page);
            pg.setPageSize(size);
            response.setPaginationData(pg);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching incomplete registrations: " + e.getMessage());
        }
        return response;
    }

    public ResultResponse updateWebsiteLead(Long userId, String status, String note) {
        ResultResponse resp = new ResultResponse();
        resp.setStatus(ResponseStatus.FAILURE);
        try {
            UserEntity u = userRepository.findById(userId).orElse(null);
            if (u == null || u.getUserStatus() != null) {
                resp.setCode(404);
                resp.setMessage("Lead not found");
                return resp;
            }
            if (status != null && !status.isBlank()) {
                String st = status.trim().toUpperCase();
                if (!LEAD_STATUSES.contains(st)) {
                    resp.setCode(400);
                    resp.setMessage("status must be NEW, CONTACTED, CONVERTED or DROPPED");
                    return resp;
                }
                u.setLeadStatus(st);
            }
            if (note != null) u.setLeadNote(note.length() > 500 ? note.substring(0, 500) : note);
            userRepository.save(u);
            resp.setCode(200);
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setMessage("Lead updated");
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Error updating lead: " + e.getMessage());
        }
        return resp;
    }

    public ResultResponse assignPlanToUser(Long userId, Long planId, Long adminId) {
        ResultResponse response = new ResultResponse();
        try {
            Optional<UserEntity> optUser = userRepository.findById(userId);
            if (optUser.isEmpty()) {
                response.setCode(404);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("User not found");
                return response;
            }
            Optional<SubscriptionPlan> optPlan = subscriptionPlanRepository.findById(planId);
            if (optPlan.isEmpty()) {
                response.setCode(404);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("Subscription plan not found with ID: " + planId);
                return response;
            }
            UserEntity user = optUser.get();
            SubscriptionPlan plan = optPlan.get();

            // Shared activation: subscription row + email + Direct push + in-app notification.
            subscriptionActivationService.activatePlan(user, plan,
                    "ADMIN_ASSIGN" + (adminId != null ? "_BY_" + adminId : ""), "PLAN_ASSIGNED");

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage(plan.getTitle() + " plan assigned to " + user.getFirstName());
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error assigning plan: " + e.getMessage());
        }
        return response;
    }

    /**
     * Member's full name for outbound email (client 2026-09-09: every mail should
     * address them by first AND last name, not just the first). Falls back to
     * whichever half exists, and finally to "there" so a greeting never renders
     * as "Dear ,".
     */
    private String fullName(UserEntity u) {
        String first = u.getFirstName() != null ? u.getFirstName().trim() : "";
        String last = u.getLastName() != null ? u.getLastName().trim() : "";
        String full = (first + " " + last).trim();
        return full.isEmpty() ? "there" : full;
    }

    public ResultResponse rejectUser(Long userId, String reason) {
        ResultResponse response = new ResultResponse();
        try {
            if (reason == null || reason.isBlank()) {
                response.setCode(400);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("Rejection reason is required");
                return response;
            }

            Optional<UserEntity> optUser = userRepository.findById(userId);
            if (optUser.isEmpty()) {
                response.setCode(404);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("User not found");
                return response;
            }
            UserEntity user = optUser.get();
            // Mirror the admin panel's 250-char cap server-side — the member sees
            // this verbatim on the rejection screen and in the rejection email.
            String trimmedReason = reason.trim();
            if (trimmedReason.length() > 250) trimmedReason = trimmedReason.substring(0, 250);
            user.setUserStatus(ApprovalStatus.REJECTED);
            user.setRejectionReason(trimmedReason);
            user.setResubmissionNote(null); // consumed — see approveUser
            userRepository.save(user);

            // Send rejection email
            if (user.getEmail() != null && !user.getEmail().isBlank()) {
                try {
                    emailService.sendRejectionEmail(user.getEmail(), fullName(user), trimmedReason);
                } catch (Exception emailEx) {
                    System.out.println("Warning: rejection email failed: " + emailEx.getMessage());
                }
            }
            try { whatsAppService.profileRejected(user, trimmedReason); } catch (Exception ignored) { }

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("User rejected successfully");
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error rejecting user: " + e.getMessage());
        }
        return response;
    }

    // ==================== PAYMENT MANAGEMENT ====================

    public PaginatedResultResponse getAllPayments(int page, int size, PaymentRequestStatus status) {
        PaginatedResultResponse response = new PaginatedResultResponse();
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<PaymentRequestEntity> payments;
            if (status != null) {
                payments = paymentRequestRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
            } else {
                payments = paymentRequestRepository.findAllByOrderByCreatedAtDesc(pageable);
            }

            // Enrich with user info
            List<Map<String, Object>> enriched = new ArrayList<>();
            for (PaymentRequestEntity payment : payments.getContent()) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", payment.getId());
                item.put("userId", payment.getUserId());
                item.put("planId", payment.getPlanId());
                item.put("amount", payment.getAmount());
                item.put("utrNumber", payment.getUtrNumber());
                item.put("screenshotUrl", payment.getScreenshotUrl());
                item.put("status", payment.getStatus());
                item.put("createdAt", payment.getCreatedAt());
                item.put("verifiedAt", payment.getVerifiedAt());

                // Add user name
                UserEntity user = userRepository.findById(payment.getUserId()).orElse(null);
                if (user != null) {
                    item.put("userName", user.getFirstName() + " " + user.getLastName());
                    item.put("userMobile", user.getMobile());
                }

                // Add plan name
                SubscriptionPlan plan = subscriptionPlanRepository.findById(payment.getPlanId()).orElse(null);
                if (plan != null) {
                    item.put("planName", plan.getTitle());
                }

                enriched.add(item);
            }

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Payments fetched successfully");
            response.setData(enriched);

            PaginationData pagination = new PaginationData();
            pagination.setTotalPages(payments.getTotalPages());
            pagination.setTotalElements(payments.getTotalElements());
            pagination.setCurrentPage(payments.getNumber());
            pagination.setPageSize(payments.getSize());
            response.setPaginationData(pagination);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching payments: " + e.getMessage());
        }
        return response;
    }

    public ResultResponse getPaymentStats() {
        ResultResponse response = new ResultResponse();
        try {
            List<PaymentRequestEntity> allPayments = paymentRequestRepository.findAll();

            BigDecimal totalRevenue = allPayments.stream()
                .filter(p -> p.getStatus() == PaymentRequestStatus.APPROVED)
                .map(PaymentRequestEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            long totalTransactions = allPayments.size();
            long approvedCount = allPayments.stream().filter(p -> p.getStatus() == PaymentRequestStatus.APPROVED).count();
            long pendingCount = allPayments.stream().filter(p -> p.getStatus() == PaymentRequestStatus.PENDING).count();
            long rejectedCount = allPayments.stream().filter(p -> p.getStatus() == PaymentRequestStatus.REJECTED).count();

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalRevenue", totalRevenue);
            stats.put("totalTransactions", totalTransactions);
            stats.put("approvedCount", approvedCount);
            stats.put("pendingCount", pendingCount);
            stats.put("rejectedCount", rejectedCount);

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Payment stats fetched successfully");
            response.setData(stats);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching payment stats: " + e.getMessage());
        }
        return response;
    }

    public ResultResponse rejectPayment(AdminApprovePaymentRequestDTO request) {
        ResultResponse response = new ResultResponse();
        try {
            Optional<PaymentRequestEntity> optPaymentReq = paymentRequestRepository.findById(request.getPaymentRequestId());
            if (optPaymentReq.isEmpty()) {
                response.setCode(404);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("Payment Request not found");
                return response;
            }

            PaymentRequestEntity paymentReq = optPaymentReq.get();
            if (paymentReq.getStatus() != PaymentRequestStatus.PENDING) {
                response.setCode(400);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("Payment Request is not in PENDING status");
                return response;
            }

            paymentReq.setStatus(PaymentRequestStatus.REJECTED);
            paymentReq.setVerifiedAt(LocalDateTime.now());
            paymentReq.setVerifiedBy(request.getAdminId());
            paymentRequestRepository.save(paymentReq);

            // Push notification (bypass plan gate — system notification)
            pushNotificationService.sendPushNotificationToUserDirect(
                paymentReq.getUserId(),
                "Payment Rejected",
                "Your payment request has been rejected. Please contact support for details."
            );

            // In-app notification
            try {
                Notification notif = new Notification();
                notif.setReceiverId(paymentReq.getUserId());
                notif.setTitle("Payment Rejected");
                notif.setMessage("Your payment request has been rejected. Please contact support for details.");
                notif.setNotificationCategory("PAYMENT_REJECTED");
                notif.setIsRead(ActiveStatus.N);
                notificationRepository.save(notif);
            } catch (Exception ignored) { }

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Payment rejected successfully");
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error rejecting payment: " + e.getMessage());
        }
        return response;
    }

    // ==================== SUBSCRIPTION MANAGEMENT ====================

    public PaginatedResultResponse getAllUserSubscriptions(int page, int size) {
        PaginatedResultResponse response = new PaginatedResultResponse();
        try {
            // Newest first. Unsorted, findAll came back oldest-first, and the admin page asks
            // for one page of 50 — so a subscription bought TODAY sat at row ~228 and was
            // never shown (client, 2026-09-27: could not see a member's plan rows at all).
            Pageable pageable = PageRequest.of(page, size,
                    org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
            Page<UserSubscriptions> subscriptions = userSubscriptionsRepository.findAll(pageable);

            List<Map<String, Object>> enriched = new ArrayList<>();
            for (UserSubscriptions sub : subscriptions.getContent()) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", sub.getId());
                item.put("userId", sub.getUserId());
                item.put("status", sub.getStatus());
                item.put("startDate", sub.getStartDate());
                item.put("endDate", sub.getEndDate());
                item.put("createdAt", sub.getCreatedAt());

                UserEntity user = userRepository.findById(sub.getUserId()).orElse(null);
                if (user != null) {
                    item.put("userName", user.getFirstName() + " " + user.getLastName());
                }

                SubscriptionPlan plan = subscriptionPlanRepository.findById(sub.getSubscriptionPlanId()).orElse(null);
                if (plan != null) {
                    item.put("planName", plan.getTitle());
                    item.put("planPrice", plan.getPrice());
                }

                enriched.add(item);
            }

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("User subscriptions fetched successfully");
            response.setData(enriched);

            PaginationData pagination = new PaginationData();
            pagination.setTotalPages(subscriptions.getTotalPages());
            pagination.setTotalElements(subscriptions.getTotalElements());
            pagination.setCurrentPage(subscriptions.getNumber());
            pagination.setPageSize(subscriptions.getSize());
            response.setPaginationData(pagination);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching subscriptions: " + e.getMessage());
        }
        return response;
    }

    // ==================== REPORTS ====================

    public PaginatedResultResponse getAllReports(int page, int size) {
        PaginatedResultResponse response = new PaginatedResultResponse();
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<Object[]> reports = userReportRepository.findAllReportsForAdmin(pageable);

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Reports fetched successfully");
            response.setData(reports.getContent());

            PaginationData pagination = new PaginationData();
            pagination.setTotalPages(reports.getTotalPages());
            pagination.setTotalElements(reports.getTotalElements());
            pagination.setCurrentPage(reports.getNumber());
            pagination.setPageSize(reports.getSize());
            response.setPaginationData(pagination);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching reports: " + e.getMessage());
        }
        return response;
    }

    // ==================== RESTRICTED FIELD REQUESTS ====================

    public PaginatedResultResponse getAllRestrictedFieldRequests(String fieldType, int page, int size) {
        PaginatedResultResponse response = new PaginatedResultResponse();
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<RestrictedFieldRequest> requests;
            if (fieldType != null && !fieldType.isEmpty()) {
                requests = restrictedFieldRequestRepository.findByFieldType(fieldType, pageable);
            } else {
                requests = restrictedFieldRequestRepository.findAll(pageable);
            }

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Restricted field requests fetched successfully");
            response.setData(requests.getContent());

            PaginationData pagination = new PaginationData();
            pagination.setTotalPages(requests.getTotalPages());
            pagination.setTotalElements(requests.getTotalElements());
            pagination.setCurrentPage(requests.getNumber());
            pagination.setPageSize(requests.getSize());
            response.setPaginationData(pagination);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching requests: " + e.getMessage());
        }
        return response;
    }

    // ==================== IMAGE VERIFICATION ====================

    public PaginatedResultResponse getPendingImageVerifications(int page, int size) {
        PaginatedResultResponse response = new PaginatedResultResponse();
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<GalleryEntity> images = galleryRepository.findByIsActive(ActiveStatus.Y, pageable);

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Image verifications fetched successfully");
            response.setData(images.getContent());

            PaginationData pagination = new PaginationData();
            pagination.setTotalPages(images.getTotalPages());
            pagination.setTotalElements(images.getTotalElements());
            pagination.setCurrentPage(images.getNumber());
            pagination.setPageSize(images.getSize());
            response.setPaginationData(pagination);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching image verifications: " + e.getMessage());
        }
        return response;
    }

    public ResultResponse approveImage(Long galleryId) {
        ResultResponse response = new ResultResponse();
        try {
            Optional<GalleryEntity> optImage = galleryRepository.findById(galleryId);
            if (optImage.isEmpty()) {
                response.setCode(404);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("Image not found");
                return response;
            }
            GalleryEntity image = optImage.get();
            image.setIsActive(ActiveStatus.Y);
            galleryRepository.save(image);

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Image approved successfully");
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error approving image: " + e.getMessage());
        }
        return response;
    }

    public ResultResponse rejectImage(Long galleryId, String reason) {
        ResultResponse response = new ResultResponse();
        try {
            Optional<GalleryEntity> optImage = galleryRepository.findById(galleryId);
            if (optImage.isEmpty()) {
                response.setCode(404);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("Image not found");
                return response;
            }
            GalleryEntity image = optImage.get();
            image.setIsActive(ActiveStatus.N);
            galleryRepository.save(image);

            // Clean up from user profile if it matches the main profileImage
            Optional<UserEntity> optUser = userRepository.findById(image.getUserId());
            if (optUser.isPresent()) {
                UserEntity user = optUser.get();
                if (user.getProfileImage() != null && user.getProfileImage().equals(image.getUserImage())) {
                    user.setProfileImage(null);
                    user.setThumbnailImage(null);
                    userRepository.save(user);
                }
            }

            // Save in-app notification
            try {
                Notification notif = new Notification();
                notif.setReceiverId(image.getUserId());
                notif.setTitle("Photo Rejected");
                String notifMsg = "Your uploaded photo was rejected by the admin.";
                if (reason != null && !reason.trim().isEmpty()) {
                    notifMsg += " Reason: " + reason.trim();
                }
                notif.setMessage(notifMsg);
                notif.setNotificationCategory("IMAGE_REJECTED");
                notif.setIsRead(ActiveStatus.N);
                notificationRepository.save(notif);
            } catch (Exception ignored) { }

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Image rejected successfully");
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error rejecting image: " + e.getMessage());
        }
        return response;
    }

    // ==================== AUDIT LOGS ====================

    public PaginatedResultResponse getAuditLogs(int page, int size, String module, String search) {
        PaginatedResultResponse response = new PaginatedResultResponse();
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<AuditLog> logs;
            if (module != null && !module.isEmpty()) {
                logs = auditLogRepository.findByModuleOrderByCreatedAtDesc(module, pageable);
            } else if (search != null && !search.isEmpty()) {
                logs = auditLogRepository.findByActionContainingIgnoreCaseOrderByCreatedAtDesc(search, pageable);
            } else {
                logs = auditLogRepository.findAllByOrderByCreatedAtDesc(pageable);
            }

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Audit logs fetched successfully");
            response.setData(logs.getContent());

            PaginationData pagination = new PaginationData();
            pagination.setTotalPages(logs.getTotalPages());
            pagination.setTotalElements(logs.getTotalElements());
            pagination.setCurrentPage(logs.getNumber());
            pagination.setPageSize(logs.getSize());
            response.setPaginationData(pagination);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching audit logs: " + e.getMessage());
        }
        return response;
    }

    public void logAdminAction(Long adminId, String adminName, String action, String module, String details, Long targetId) {
        AuditLog log = new AuditLog();
        log.setAdminId(adminId);
        log.setAdminName(adminName);
        log.setAction(action);
        log.setModule(module);
        log.setDetails(details);
        log.setTargetId(targetId);
        auditLogRepository.save(log);
    }

    // ==================== CMS PAGES ====================

    public ResultResponse getAllCmsPages() {
        ResultResponse response = new ResultResponse();
        try {
            List<CmsPage> pages = cmsPageRepository.findAll();
            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("CMS pages fetched successfully");
            response.setData(pages);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching CMS pages: " + e.getMessage());
        }
        return response;
    }

    public ResultResponse getCmsPageBySlug(String slug) {
        ResultResponse response = new ResultResponse();
        try {
            Optional<CmsPage> optPage = cmsPageRepository.findBySlug(slug);
            if (optPage.isEmpty()) {
                response.setCode(404);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("CMS page not found: " + slug);
                return response;
            }
            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("CMS page fetched successfully");
            response.setData(optPage.get());
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching CMS page: " + e.getMessage());
        }
        return response;
    }

    public ResultResponse updateCmsPage(String slug, CmsPage updatedPage) {
        ResultResponse response = new ResultResponse();
        try {
            Optional<CmsPage> optPage = cmsPageRepository.findBySlug(slug);
            CmsPage page;
            if (optPage.isPresent()) {
                page = optPage.get();
                page.setTitle(updatedPage.getTitle());
                page.setContent(updatedPage.getContent());
                page.setIsActive(updatedPage.getIsActive());
                page.setUpdatedBy(updatedPage.getUpdatedBy());
            } else {
                page = updatedPage;
                page.setSlug(slug);
            }
            cmsPageRepository.save(page);

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("CMS page updated successfully");
            response.setData(page);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error updating CMS page: " + e.getMessage());
        }
        return response;
    }

    // ==================== SUPPORT TICKETS ====================

    public PaginatedResultResponse getSupportTickets(int page, int size, String status) {
        PaginatedResultResponse response = new PaginatedResultResponse();
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<SupportTicket> tickets;
            if (status != null && !status.isEmpty()) {
                tickets = supportTicketRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
            } else {
                tickets = supportTicketRepository.findAllByOrderByCreatedAtDesc(pageable);
            }

            // Enrich with user name
            List<Map<String, Object>> enriched = new ArrayList<>();
            for (SupportTicket ticket : tickets.getContent()) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", ticket.getId());
                item.put("userId", ticket.getUserId());
                item.put("subject", ticket.getSubject());
                item.put("category", ticket.getCategory());
                item.put("priority", ticket.getPriority());
                item.put("status", ticket.getStatus());
                item.put("createdAt", ticket.getCreatedAt());
                item.put("updatedAt", ticket.getUpdatedAt());

                UserEntity user = userRepository.findById(ticket.getUserId()).orElse(null);
                if (user != null) {
                    item.put("userName", user.getFirstName() + " " + user.getLastName());
                }

                enriched.add(item);
            }

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Support tickets fetched successfully");
            response.setData(enriched);

            PaginationData pagination = new PaginationData();
            pagination.setTotalPages(tickets.getTotalPages());
            pagination.setTotalElements(tickets.getTotalElements());
            pagination.setCurrentPage(tickets.getNumber());
            pagination.setPageSize(tickets.getSize());
            response.setPaginationData(pagination);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching support tickets: " + e.getMessage());
        }
        return response;
    }

    public ResultResponse getTicketDetail(Long ticketId) {
        ResultResponse response = new ResultResponse();
        try {
            Optional<SupportTicket> optTicket = supportTicketRepository.findById(ticketId);
            if (optTicket.isEmpty()) {
                response.setCode(404);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("Ticket not found");
                return response;
            }

            SupportTicket ticket = optTicket.get();
            List<TicketMessage> messages = ticketMessageRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);

            Map<String, Object> detail = new HashMap<>();
            detail.put("ticket", ticket);
            detail.put("messages", messages);

            UserEntity user = userRepository.findById(ticket.getUserId()).orElse(null);
            if (user != null) {
                detail.put("userName", user.getFirstName() + " " + user.getLastName());
            }

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Ticket detail fetched successfully");
            response.setData(detail);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching ticket detail: " + e.getMessage());
        }
        return response;
    }

    public ResultResponse replyToTicket(Long ticketId, TicketMessage message) {
        ResultResponse response = new ResultResponse();
        try {
            Optional<SupportTicket> optTicket = supportTicketRepository.findById(ticketId);
            if (optTicket.isEmpty()) {
                response.setCode(404);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("Ticket not found");
                return response;
            }

            message.setTicketId(ticketId);
            message.setSenderType("ADMIN");
            ticketMessageRepository.save(message);

            // Update ticket status to IN_PROGRESS if still OPEN
            SupportTicket ticket = optTicket.get();
            if ("OPEN".equals(ticket.getStatus())) {
                ticket.setStatus("IN_PROGRESS");
                supportTicketRepository.save(ticket);
            }

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Reply sent successfully");
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error replying to ticket: " + e.getMessage());
        }
        return response;
    }

    public ResultResponse closeTicket(Long ticketId) {
        ResultResponse response = new ResultResponse();
        try {
            Optional<SupportTicket> optTicket = supportTicketRepository.findById(ticketId);
            if (optTicket.isEmpty()) {
                response.setCode(404);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("Ticket not found");
                return response;
            }

            SupportTicket ticket = optTicket.get();
            ticket.setStatus("CLOSED");
            ticket.setClosedAt(LocalDateTime.now());
            supportTicketRepository.save(ticket);

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Ticket closed successfully");
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error closing ticket: " + e.getMessage());
        }
        return response;
    }

    // ==================== NOTIFICATIONS (Admin View) ====================

    public PaginatedResultResponse getAdminNotifications(int page, int size) {
        PaginatedResultResponse response = new PaginatedResultResponse();
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<Notification> notifications = notificationRepository.findAll(pageable);

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Notifications fetched successfully");
            response.setData(notifications.getContent());

            PaginationData pagination = new PaginationData();
            pagination.setTotalPages(notifications.getTotalPages());
            pagination.setTotalElements(notifications.getTotalElements());
            pagination.setCurrentPage(notifications.getNumber());
            pagination.setPageSize(notifications.getSize());
            response.setPaginationData(pagination);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching notifications: " + e.getMessage());
        }
        return response;
    }

    // ==================== SETTINGS ====================

    public ResultResponse getSettings() {
        ResultResponse response = new ResultResponse();
        try {
            Optional<KeyValue> optSettings = keyValueRepository.findByKeyColumn("AppSettings");
            if (optSettings.isPresent()) {
                response.setCode(200);
                response.setStatus(ResponseStatus.SUCCESS);
                response.setMessage("Settings fetched successfully");
                response.setData(optSettings.get());
            } else {
                response.setCode(404);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("Settings not found. Create a KeyValue entry with key 'AppSettings'.");
            }
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching settings: " + e.getMessage());
        }
        return response;
    }

    public ResultResponse updateSettings(Map<String, Object> settings) {
        ResultResponse response = new ResultResponse();
        try {
            Optional<KeyValue> optSettings = keyValueRepository.findByKeyColumn("AppSettings");
            KeyValue kv;
            if (optSettings.isPresent()) {
                kv = optSettings.get();
            } else {
                kv = new KeyValue();
                kv.setKeyColumn("AppSettings");
            }
            kv.setValueColumn(new ObjectMapper().writeValueAsString(settings));
            keyValueRepository.save(kv);

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Settings updated successfully");
            response.setData(kv);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error updating settings: " + e.getMessage());
        }
        return response;
    }
}
