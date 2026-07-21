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

    public PaginatedResultResponse getAllUsersForAdmin(int page, int size, String userType, String userStatus) {
        PaginatedResultResponse response = new PaginatedResultResponse();
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<Object[]> userPage = userRepository.findAllUsersForAdmin(userType, userStatus, pageable);

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

            // EmployedAt
            if (request.getEmployedAt() != null && !request.getEmployedAt().isEmpty()) {
                userDetail.setEmployedAt(mapEmploymentType(request.getEmployedAt()));
            }

            ObjectMapper mapper = new ObjectMapper();

            // Family Info JSON
            Map<String, String> familyInfoMap = new HashMap<>();
            familyInfoMap.put("fatherName", nullToEmpty(request.getFatherName()));
            familyInfoMap.put("motherName", nullToEmpty(request.getMotherName()));
            familyInfoMap.put("father", nullToEmpty(request.getFatherName()));
            familyInfoMap.put("mother", nullToEmpty(request.getMotherName()));
            familyInfoMap.put("father_occupation", "");
            familyInfoMap.put("mother_occupation", "");
            familyInfoMap.put("house", "");
            familyInfoMap.put("familyType", "");
            familyInfoMap.put("family_status", "");
            familyInfoMap.put("no_of_brother", "");
            familyInfoMap.put("no_of_sister", "");
            familyInfoMap.put("brother_married", "");
            familyInfoMap.put("sister_married", "");
            userDetail.setFamilyInfo(mapper.writeValueAsString(familyInfoMap));

            // Basic Info JSON
            Map<String, String> basicInfoMap = new HashMap<>();
            basicInfoMap.put("place_of_birth", nullToEmpty(request.getPlaceOfBirth()));
            basicInfoMap.put("mother_language", nullToEmpty(request.getMotherLanguage()));
            basicInfoMap.put("marital_status", "");
            basicInfoMap.put("physical_status", "");
            userDetail.setBasicInfo(mapper.writeValueAsString(basicInfoMap));

            // Astro Info JSON (as List of Map, matching existing pattern)
            Map<String, String> astroInfoMap = new HashMap<>();
            astroInfoMap.put("star", nullToEmpty(request.getStar()));
            astroInfoMap.put("dosham", nullToEmpty(request.getDosham()));
            astroInfoMap.put("moon_sign", "");
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
            if (request.getProfileCreatedFor() != null)
                user.setProfileCreated(request.getProfileCreatedFor());
            if (request.getProfileImage() != null)
                user.setProfileImage(request.getProfileImage());
            if (request.getUserStatus() != null) {
                user.setUserStatus(ApprovalStatus.valueOf(request.getUserStatus()));
            }

            userRepository.save(user);

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

            if (request.getEmployedAt() != null && !request.getEmployedAt().isEmpty()) {
                userDetail.setEmployedAt(mapEmploymentType(request.getEmployedAt()));
            }

            ObjectMapper mapper = new ObjectMapper();

            // Family Info JSON
            if (request.getFatherName() != null || request.getMotherName() != null) {
                Map<String, String> familyInfoMap = new HashMap<>();
                // Try to preserve existing data
                if (userDetail.getFamilyInfo() != null) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, String> existing = mapper.readValue(userDetail.getFamilyInfo(), Map.class);
                        familyInfoMap.putAll(existing);
                    } catch (Exception ignored) {
                    }
                }
                if (request.getFatherName() != null) {
                    familyInfoMap.put("fatherName", request.getFatherName());
                    familyInfoMap.put("father", request.getFatherName());
                }
                if (request.getMotherName() != null) {
                    familyInfoMap.put("motherName", request.getMotherName());
                    familyInfoMap.put("mother", request.getMotherName());
                }
                userDetail.setFamilyInfo(mapper.writeValueAsString(familyInfoMap));
            }

            // Basic Info JSON
            if (request.getPlaceOfBirth() != null || request.getMotherLanguage() != null) {
                Map<String, String> basicInfoMap = new HashMap<>();
                if (userDetail.getBasicInfo() != null) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, String> existing = mapper.readValue(userDetail.getBasicInfo(), Map.class);
                        basicInfoMap.putAll(existing);
                    } catch (Exception ignored) {
                    }
                }
                if (request.getPlaceOfBirth() != null)
                    basicInfoMap.put("place_of_birth", request.getPlaceOfBirth());
                if (request.getMotherLanguage() != null)
                    basicInfoMap.put("mother_language", request.getMotherLanguage());
                userDetail.setBasicInfo(mapper.writeValueAsString(basicInfoMap));
            }

            // Astro Info JSON
            if (request.getStar() != null || request.getDosham() != null) {
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
                java.util.List<UserSubscriptions> activeSubs = userSubscriptionsRepository
                        .findByUserIdAndStatus(paymentReq.getUserId(), SubscriptionStatus.ACTIVE);
                if (!activeSubs.isEmpty()) {
                    UserSubscriptions sub = activeSubs.get(0);
                    int current = sub.getBoostCredits() != null ? sub.getBoostCredits() : 0;
                    sub.setBoostCredits(current + 1);
                    userSubscriptionsRepository.save(sub);
                }
                pushNotificationService.sendPushNotificationToUser(
                        paymentReq.getUserId(),
                        "Boost Ready! 🚀",
                        "Your boost credit has been added. Go to your profile and boost now!"
                );
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

            UserSubscriptions subscription = new UserSubscriptions();
            subscription.setUserId(paymentReq.getUserId());
            subscription.setSubscriptionPlanId(plan.getId());
            subscription.setStatus(SubscriptionStatus.ACTIVE);
            subscription.setAutoRenew(ActiveStatus.N);
            subscription.setStartDate(LocalDate.now());
            subscription.setEndDate(LocalDate.now().plusDays(plan.getDurationDays()));
            subscription.setPaymentReference("PAY_REQ_" + paymentReq.getId());

            userSubscriptionsRepository.save(subscription);

            // Send push notification to user about payment approval (bypass plan gate)
            pushNotificationService.sendPushNotificationToUserDirect(
                paymentReq.getUserId(),
                "Payment Approved! 🎉",
                "Your " + plan.getTitle() + " plan is now active. Enjoy premium features!"
            );

            // In-app notification
            try {
                Notification notif = new Notification();
                notif.setReceiverId(paymentReq.getUserId());
                notif.setTitle("Payment Approved");
                notif.setMessage("Your " + plan.getTitle() + " plan is now active. Enjoy premium features!");
                notif.setNotificationCategory("PAYMENT_APPROVED");
                notif.setIsRead(ActiveStatus.N);
                notificationRepository.save(notif);
            } catch (Exception ignored) { }

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
            userRepository.save(user);

            // Send approval email
            if (user.getEmail() != null && !user.getEmail().isBlank()) {
                try {
                    emailService.sendApprovalEmail(user.getEmail(), user.getFirstName());
                } catch (Exception emailEx) {
                    System.out.println("Warning: approval email failed: " + emailEx.getMessage());
                }
            }

            // Send push notification if user has any registered device
            try {
                pushNotificationService.sendPushNotificationToUser(
                    userId,
                    "Profile Approved! 🎉",
                    "Your profile has been approved. You can now explore matches and connect!"
                );
            } catch (Exception pushEx) {
                System.out.println("Warning: approval push failed: " + pushEx.getMessage());
            }

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
            user.setUserStatus(ApprovalStatus.REJECTED);
            user.setRejectionReason(reason);
            userRepository.save(user);

            // Send rejection email
            if (user.getEmail() != null && !user.getEmail().isBlank()) {
                try {
                    emailService.sendRejectionEmail(user.getEmail(), user.getFirstName(), reason);
                } catch (Exception emailEx) {
                    System.out.println("Warning: rejection email failed: " + emailEx.getMessage());
                }
            }

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
            Pageable pageable = PageRequest.of(page, size);
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
