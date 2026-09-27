package com.uravugal.matrimony.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uravugal.matrimony.dtos.FilteredUserPlanView;
import com.uravugal.matrimony.dtos.FilteredUserResponse;
import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.dtos.UserFilterRequest;
import com.uravugal.matrimony.dtos.UserDetailUpdateRequest;
import com.uravugal.matrimony.dtos.UserProfileRequest;
import com.uravugal.matrimony.enums.ActiveStatus;
import com.uravugal.matrimony.enums.ApprovalStatus;
import com.uravugal.matrimony.enums.EmploymentType;
import com.uravugal.matrimony.enums.Gender;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.Features;
import com.uravugal.matrimony.models.InterestRequest;
import com.uravugal.matrimony.models.PlanFeatures;
import com.uravugal.matrimony.models.SubCasteEntity;
import com.uravugal.matrimony.models.UserDetailEntity;
import com.uravugal.matrimony.models.UserEntity;
import com.uravugal.matrimony.models.UserSubscriptions;
import com.uravugal.matrimony.repositories.FeaturesRepository;
import com.uravugal.matrimony.repositories.PlanFeaturesRepository;
import com.uravugal.matrimony.repositories.UserRepository;
import com.uravugal.matrimony.repositories.UserSubscriptionsRepository;
import com.uravugal.matrimony.repositories.InterestRequestRepository;
import com.uravugal.matrimony.repositories.ShortlistedProfileRepository;
import com.uravugal.matrimony.repositories.UserDetailRepository;
import com.uravugal.matrimony.repositories.UserLikesRepository;
import com.uravugal.matrimony.utils.EncryptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.uravugal.matrimony.enums.IsUser;
import org.springframework.web.multipart.MultipartFile;

import com.uravugal.matrimony.models.UserEntity;

@Service
public class UserService {

    private static final String PREFIX = "UMR";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.uravugal.matrimony.repositories.DeletedAccountRepository deletedAccountRepository;

    @Autowired
    private UserDetailRepository userDetailRepository;

    @Autowired
    private S3FileUploadService s3UploadService;

    @Autowired
    private UserSubscriptionsRepository userSubscriptionRepository;

    @Autowired
    private UserLikesRepository userLikeRepository;

    @Autowired
    private ShortlistedProfileRepository shortlistedProfileRepository;

    @Autowired
    private InterestRequestRepository interestRequestRepository;

    @Autowired
    private com.uravugal.matrimony.repositories.UserFeatureUsageRepository userFeatureUsageRepository;

    @Autowired
    private FamilyLoginService familyLoginService;

    @Autowired
    private com.uravugal.matrimony.utils.JwtUtil jwtUtil;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    @Autowired
    private com.uravugal.matrimony.repositories.BlockedUserRepository blockedUserRepository;

    @Autowired
    private com.uravugal.matrimony.repositories.ProfileBoostRepository profileBoostRepository;

    @Autowired
    private FeaturesRepository featuresRepository;

    @Autowired
    private PlanFeaturesRepository planFeaturesRepository;

    @Autowired
    private com.uravugal.matrimony.repositories.SubCasteRepository subCasteRepository;

    private static final String AWS_BASE_PATH = "profile-images/";

    /**
     * Fills the transient subcasteName on a user so the app can render the subcaste without a
     * second lookup. Null-safe and fail-soft — a missing/deleted subcaste row just leaves the
     * name null rather than breaking the whole profile response.
     */
    private void resolveSubcasteName(UserEntity user) {
        try {
            if (user != null && user.getSubcasteId() != null) {
                SubCasteEntity subCaste = subCasteRepository.findById(user.getSubcasteId()).orElse(null);
                if (subCaste != null) {
                    user.setSubcasteName(subCaste.getSubcasteName());
                }
            }
        } catch (Exception ignored) { }
    }

    public ResultResponse sendOtp(String mobileNumber) {
        ResultResponse resp = new ResultResponse();
        try {
            // Check if user with this mobile number exists
            UserEntity user = userRepository.findByMobile(mobileNumber);

            if (user == null) {
                resp.setCode(404);
                resp.setMessage("User not found with this mobile number");
                resp.setStatus(ResponseStatus.FAILURE);
                return resp;
            }

            // Generate OTP
            String otp = generateOTP();

            user.setOtp(Integer.parseInt(otp));
            userRepository.save(user);

            // Here you would typically send the OTP to the mobile number
            // For now, we'll just return it in the response
            resp.setCode(200);
            resp.setMessage("OTP sent successfully");
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setData(otp);

        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    private String generateOTP() {
        Random random = new Random();
        return String.format("%04d", random.nextInt(9999));
    }

    public ResultResponse changePin(String mobileNumber, String pin) {
        ResultResponse resp = new ResultResponse();
        try {
            // Find user by mobile number
            UserEntity user = userRepository.findByMobile(mobileNumber);

            if (user == null) {
                resp.setCode(404);
                resp.setMessage("User not found with this mobile number");
                resp.setStatus(ResponseStatus.FAILURE);
                return resp;
            }

            // Encrypt the PIN before saving
            String encodedPin = Base64.getEncoder().encodeToString(pin.getBytes());
            user.setPin(encodedPin);
            userRepository.save(user);

            resp.setCode(200);
            resp.setMessage("PIN updated successfully");
            resp.setStatus(ResponseStatus.SUCCESS);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    /**
     * Member-initiated resubmission after an admin rejection. Flips REJECTED
     * back to PENDING so the profile re-enters the admin verification queue,
     * and stores an optional <=250-char note from the member to the admin.
     * The endpoint the app has always called — POST /user/resubmit-profile —
     * did not exist until 2026-09-04, so every resubmit failed with a 404.
     */
    public ResultResponse resubmitProfile(String encodedUserId, String note) {
        ResultResponse resp = new ResultResponse();
        try {
            Long userId = Long.parseLong(new String(Base64.getDecoder().decode(encodedUserId)));
            UserEntity user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                resp.setCode(404);
                resp.setMessage("User not found");
                resp.setStatus(ResponseStatus.FAILURE);
                return resp;
            }
            if (user.getUserStatus() == ApprovalStatus.APPROVED) {
                resp.setCode(400);
                resp.setMessage("Your profile is already approved");
                resp.setStatus(ResponseStatus.FAILURE);
                return resp;
            }
            // Cap server-side — the app limits the input to 250 chars, but the
            // column is 250 and a longer value must never fail the save.
            if (note != null) {
                note = note.trim();
                if (note.length() > 250) note = note.substring(0, 250);
                if (note.isEmpty()) note = null;
            }
            user.setResubmissionNote(note);
            // PENDING resubmits are a no-op on status (double-tap / already in
            // queue) but still record the member's note.
            user.setUserStatus(ApprovalStatus.PENDING);
            userRepository.save(user);

            resp.setCode(200);
            resp.setMessage("Profile resubmitted for review");
            resp.setStatus(ResponseStatus.SUCCESS);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    /**
     * Settings → Change PIN (2026-09-05): the signed-in member must prove the
     * CURRENT pin before it changes. Kept separate from changePin so the
     * forgot-PIN/reset flows (which have no old pin to give) are untouched.
     */
    public ResultResponse changePinWithOld(String mobileNumber, String oldPin, String newPin) {
        ResultResponse resp = new ResultResponse();
        try {
            UserEntity user = userRepository.findByMobile(mobileNumber);
            if (user == null) {
                resp.setCode(404);
                resp.setMessage("User not found with this mobile number");
                resp.setStatus(ResponseStatus.FAILURE);
                return resp;
            }
            String stored = user.getPin();
            String decodedStored = "";
            try {
                decodedStored = stored != null ? new String(Base64.getDecoder().decode(stored)) : "";
            } catch (Exception ignore) { }
            if (oldPin == null || !oldPin.equals(decodedStored)) {
                resp.setCode(401);
                resp.setMessage("Current PIN is incorrect");
                resp.setStatus(ResponseStatus.FAILURE);
                return resp;
            }
            user.setPin(Base64.getEncoder().encodeToString(newPin.getBytes()));
            userRepository.save(user);
            resp.setCode(200);
            resp.setMessage("PIN updated successfully");
            resp.setStatus(ResponseStatus.SUCCESS);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    public ResultResponse verifyOtp(String mobileNumber, String otp) {
        ResultResponse resp = new ResultResponse();
        try {
            // Find user by mobile number
            UserEntity user = userRepository.findByMobile(mobileNumber);

            if (user == null) {
                resp.setCode(404);
                resp.setMessage("User not found with this mobile number");
                resp.setStatus(ResponseStatus.FAILURE);
                return resp;
            }

            // Check if OTP matches
            if (user.getOtp() != null && user.getOtp().toString().equals(otp)) {
                resp.setCode(200);
                resp.setMessage("OTP verified successfully");
                resp.setStatus(ResponseStatus.SUCCESS);

                // Clear OTP after successful verification
                user.setOtp(null);
                userRepository.save(user);
            } else {
                resp.setCode(400);
                resp.setMessage("Invalid OTP");
                resp.setStatus(ResponseStatus.FAILURE);
            }
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    public ResultResponse saveOrUpdateUser(UserEntity userEntity) {
        ResultResponse resp = new ResultResponse();
        try {
            if (userEntity.getUserId() != null) {

                UserEntity existingUser = userRepository.findById(userEntity.getUserId()).get();
                if (existingUser != null) {
                    existingUser.setCasteId(userEntity.getCasteId());
                    // existingUser.setApprovalStatus(userEntity.getApprovalStatus());
                    existingUser.setDob(userEntity.getDob());
                    existingUser.setFirstName(userEntity.getFirstName());
                    existingUser.setGender(userEntity.getGender());
                    existingUser.setLastName(userEntity.getLastName());
                    existingUser.setLocation(userEntity.getLocation());
                    existingUser.setProfileImage(userEntity.getProfileImage());
                    existingUser.setThumbnailImage(userEntity.getThumbnailImage());

                    // Encrypt PIN if provided
                    // if (userEntity.getPin() != null) {
                    // String encodedPin =
                    // Base64.getEncoder().encodeToString(userEntity.getPin().getBytes());
                    // existingUser.setPin(encodedPin);
                    // }

                    UserEntity savedUser = userRepository.save(existingUser);
                    resp.setCode(201);
                    resp.setMessage("User Data Updated Successfully.");
                    resp.setStatus(ResponseStatus.SUCCESS);
                } else {
                    resp.setCode(404);
                    resp.setMessage("User not exist.");
                    resp.setStatus(ResponseStatus.FAILURE);
                }
            } else {
                // Encrypt PIN if provided
                // if (userEntity.getPin() != null) {
                // userEntity.setPin(EncryptionUtils.encryptPin(userEntity.getPin()));
                // }

                UserEntity savedUser = userRepository.save(userEntity);
                resp.setCode(200);
                resp.setMessage("User Data Created Successfully.");
                resp.setStatus(ResponseStatus.SUCCESS);
            }
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    public ResultResponse getAllUsers() {
        ResultResponse response = new ResultResponse();
        try {
            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Fetched all users.");
            response.setData(userRepository.findAll());
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching users: " + e.getMessage());
        }
        return response;
    }

    public ResultResponse getUserById(String encodedId) {
        ResultResponse response = new ResultResponse();
        try {
            Long id;
            try {
                id = Long.parseLong(new String(Base64.getDecoder().decode(encodedId)));
            } catch (Exception e) {
                // Already a raw numeric ID
                id = Long.parseLong(encodedId);
            }

            Optional<UserEntity> user = userRepository.findById(id);
            if (user.isPresent()) {
                resolveSubcasteName(user.get());
                response.setCode(200);
                response.setStatus(ResponseStatus.SUCCESS);
                response.setMessage("User found.");
                response.setData(user.get());
            } else {
                response.setCode(404);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("User not found.");
            }
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching user: " + e.getMessage());
        }
        return response;
    }

    // Backward-compatible overload (no requester context → no masking)
    public ResultResponse getProfileDetailByUserId(Long userId) {
        return getProfileDetailByUserId(userId, null);
    }

    public ResultResponse getProfileDetailByUserId(Long viewedUserId, Long requesterId) {
        ResultResponse response = new ResultResponse();
        try {
            Optional<UserEntity> userOpt = userRepository.findById(viewedUserId);
            if (!userOpt.isPresent()) {
                response.setCode(404);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("User not found.");
                return response;
            }

            UserEntity user = userOpt.get();

            // Plan-based masking: VIEW_PERSONAL_INFO + SECURE_CONNECT
            // Own profile / null requesterId (internal call) → full data
            boolean isSelfOrInternal = (requesterId == null || requesterId.equals(viewedUserId));

            // BLOCK GATE — this overload previously had none at all, unlike
            // getProfileDetailWithIntractionStatus's equivalent check. Harmless while every
            // caller only ever uses this for self-fetches (null/self requesterId), but any future
            // caller passing a real cross-user requesterId would otherwise let a blocked pair
            // view each other's profile through this path.
            if (!isSelfOrInternal) {
                com.uravugal.matrimony.models.BlockedUser block = blockedUserRepository
                        .findByUsersEitherDirection(requesterId, viewedUserId);
                if (block != null) {
                    response.setCode(403);
                    response.setStatus(ResponseStatus.FAILURE);
                    response.setMessage("USER_BLOCKED");
                    return response;
                }
            }

            boolean hasContactAccess = isSelfOrInternal;
            boolean isSecureConnect = false;

            if (!isSelfOrInternal) {
                UserSubscriptions requesterSub = userSubscriptionRepository
                        .findTopByUserIdOrderByCreatedAtDesc(requesterId);
                Long planId = (requesterSub != null) ? requesterSub.getSubscriptionPlanId() : null;
                if (planId != null && planId != 1L) {
                    Features personalInfo = featuresRepository.findByCode("VIEW_PERSONAL_INFO");
                    if (personalInfo != null) {
                        hasContactAccess = planFeaturesRepository.findByFeatureIdAndSubscriptionPlanId(
                                personalInfo.getId(), planId) != null;
                    }
                    Features secureConnect = featuresRepository.findByCode("SECURE_CONNECT");
                    if (secureConnect != null) {
                        isSecureConnect = planFeaturesRepository.findByFeatureIdAndSubscriptionPlanId(
                                secureConnect.getId(), planId) != null;
                    }
                }
            }

            if (!hasContactAccess) {
                user.setMobile(null);
                user.setEmail(null);
            } else if (isSecureConnect && user.getMobile() != null) {
                String m = user.getMobile();
                user.setMobile("•••• •••• " + m.substring(Math.max(0, m.length() - 4)));
            }

            resolveSubcasteName(user);

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("User found.");
            response.setData(user);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching user: " + e.getMessage());
        }
        return response;
    }

    public ResultResponse getUserDetailByCaste(Integer casteId, Gender gender) {
        ResultResponse response = new ResultResponse();
        try {
            gender = gender == Gender.M ? Gender.F : Gender.M;
            List<UserEntity> user = userRepository.findAllByCasteIdAndGenderAndIsActiveAndIsUserNot(casteId, gender,
                    ActiveStatus.Y, IsUser.ADM);
            if (user.size() > 0) {
                // Boosted members first (client, 2026-09-27). This is the Explore "All Matches"
                // grid — the list members browse most — and until now a paid boost only moved
                // a profile up in Search and the Interest-Based shelf; here it did nothing and
                // the card's Boosted ribbon never had a flag to show. Stable sort: everything
                // else keeps the order it had.
                try {
                    java.util.Set<Long> boosted = new java.util.HashSet<>();
                    for (com.uravugal.matrimony.models.ProfileBoost b : profileBoostRepository.findAllByStatusAndExpiresAtAfter("ACTIVE", java.time.LocalDateTime.now())) {
                        boosted.add(b.getUserId());
                    }
                    for (UserEntity u : user) {
                        u.setHasActiveBoost(boosted.contains(u.getUserId()));
                    }
                    user = new java.util.ArrayList<>(user);
                    user.sort(java.util.Comparator.comparing((UserEntity u) -> Boolean.TRUE.equals(u.getHasActiveBoost()) ? 0 : 1));
                } catch (Exception boostErr) {
                    System.out.println("getUserDetailByCaste: boost ordering skipped — " + boostErr.getMessage());
                }
                response.setCode(200);
                response.setStatus(ResponseStatus.SUCCESS);
                response.setMessage("User Data Fetched Successfully.");
                response.setData(user);
            } else {
                response.setCode(404);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("User not found.");
            }
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching user: " + e.getMessage());
        }
        return response;
    }

    public ResultResponse getUserDetailByCasteIdAndLocation(Integer casteId, Gender gender, String location, Long requesterId) {
        ResultResponse response = new ResultResponse();
        try {
            List<UserEntity> users = userRepository.findAllByCasteIdAndGenderAndLocationAndIsActive(casteId, gender,
                    location,
                    ActiveStatus.Y);

            if (requesterId != null) {
                java.util.Set<Long> blockedIds = new java.util.HashSet<>(blockedUserRepository.findBlockAdjacentUserIds(requesterId));
                users = users.stream().filter(u -> !blockedIds.contains(u.getUserId())).collect(java.util.stream.Collectors.toList());
            }

            if (!users.isEmpty()) {
                users = users.subList(0, Math.min(users.size(), 30));
                response.setCode(200);
                response.setStatus(ResponseStatus.SUCCESS);
                response.setMessage("User Data Fetched Successfully.");
                response.setData(users);
            } else {
                response.setCode(404);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("User not found.");
            }
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching user: " + e.getMessage());
        }
        return response;
    }

    public ResultResponse getDailyShuffledUsersByCaste(Integer casteId, Gender gender, Long requesterId) {
        ResultResponse response = new ResultResponse();
        try {
            List<UserEntity> users = userRepository.findAllByCasteIdAndGenderAndIsActiveAndIsUserNot(casteId, gender,
                    ActiveStatus.Y, IsUser.ADM);

            if (requesterId != null) {
                java.util.Set<Long> blockedIds = new java.util.HashSet<>(blockedUserRepository.findBlockAdjacentUserIds(requesterId));
                users = users.stream().filter(u -> !blockedIds.contains(u.getUserId())).collect(java.util.stream.Collectors.toList());
            }

            if (!users.isEmpty()) {
                // Shuffle deterministically based on date
                long seed = LocalDate.now().toEpochDay(); // changes every day
                Collections.shuffle(users, new Random(seed));

                users = users.subList(0, Math.min(users.size(), 30));
                response.setCode(200);
                response.setStatus(ResponseStatus.SUCCESS);
                response.setMessage("Users fetched and shuffled successfully.");
                response.setData(users);
            } else {
                response.setCode(404);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("No active users found for the given caste.");
            }
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching users: " + e.getMessage());
        }

        return response;
    }

    public ResultResponse getTop30NewUsers(Integer casteId, Gender gender, Long requesterId) {
        ResultResponse response = new ResultResponse();
        try {
            List<UserEntity> users = userRepository.findTop30ByCasteIdAndGenderAndIsActiveAndIsUserNotOrderByCreatedAtDesc(casteId,
                    gender, ActiveStatus.Y, IsUser.ADM);

            if (requesterId != null) {
                java.util.Set<Long> blockedIds = new java.util.HashSet<>(blockedUserRepository.findBlockAdjacentUserIds(requesterId));
                users = users.stream().filter(u -> !blockedIds.contains(u.getUserId())).collect(java.util.stream.Collectors.toList());
            }

            if (!users.isEmpty()) {
                response.setCode(200);
                response.setStatus(ResponseStatus.SUCCESS);
                response.setMessage("Latest users fetched successfully.");
                response.setData(users);
            } else {
                response.setCode(404);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("No users found for given caste.");
            }
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Something went wrong: " + e.getMessage());
        }
        return response;
    }

    public ResultResponse getShuffledUsersWithPagination(Integer casteId, int page, int size) {
        ResultResponse response = new ResultResponse();
        try {
            // Get all active users for the caste
            List<UserEntity> users = userRepository.findAllByCasteIdAndIsActiveOrderByRandom(casteId, ActiveStatus.Y);

            if (users.isEmpty()) {
                response.setCode(404);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("No users found for the specified caste.");
                return response;
            }

            // Calculate pagination
            // int totalUsers = users.size();
            // int totalPages = (int) Math.ceil((double) totalUsers / size);

            // Calculate pagination
            int totalUsers = users.size();
            int totalPages = (int) Math.ceil((double) totalUsers / size);
            // Validate page number
            if (page < 1 || page > totalPages) {
                response.setCode(400);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("Invalid page number. Total pages available: " + totalPages);
                return response;
            }

            int startIndex = (page - 1) * size;
            int endIndex = Math.min(startIndex + size, totalUsers);

            // Get the paginated list
            List<UserEntity> paginatedUsers = users.subList(startIndex, endIndex);

            // Prepare response
            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Shuffled users fetched successfully.");
            response.setData(paginatedUsers);
            // response.setTotalPages(totalPages);
            // response.setCurrentPage(page);
            // response.setTotalItems(totalUsers);

        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching shuffled users: " + e.getMessage());
        }
        return response;
    }

    public ResultResponse getTenShuffledUsers(Gender gender, Integer casteId, Long requesterId) {
        ResultResponse response = new ResultResponse();
        try {
            // Get users filtered by gender and casteId
            List<UserEntity> users = userRepository.findAllByGenderAndCasteIdAndIsActiveOrderByRandom(gender, casteId,
                    ActiveStatus.Y);

            if (requesterId != null) {
                java.util.Set<Long> blockedIds = new java.util.HashSet<>(blockedUserRepository.findBlockAdjacentUserIds(requesterId));
                users = users.stream().filter(u -> !blockedIds.contains(u.getUserId())).collect(java.util.stream.Collectors.toList());
            }

            if (users.isEmpty()) {
                response.setCode(404);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("No active users found for the specified criteria.");
                return response;
            }

            // Take first 10 users (already shuffled by the query)
            List<UserEntity> shuffledUsers = users.subList(0, Math.min(users.size(), 10));

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Shuffled users fetched successfully.");
            response.setData(shuffledUsers);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching shuffled users: " + e.getMessage());
        }
        return response;
    }

    // public ResultResponse filterUsers(UserFilterRequest filterRequest) {
    // ResultResponse response = new ResultResponse();
    // try {
    // // Get filtered users
    // System.out.println("Filter request: " + filterRequest);
    // List<UserEntity> users = userRepository.findFilteredUsers(
    // ActiveStatus.Y,
    // filterRequest.getGender() != null ? Gender.valueOf(filterRequest.getGender())
    // : null,
    // filterRequest.getCasteId(),
    // filterRequest.getMinAge(),
    // filterRequest.getMaxAge(),
    // filterRequest.getMinAnnualIncome(),
    // filterRequest.getMaxAnnualIncome(),
    // filterRequest.getOccupation(),
    // filterRequest.getLocation(),
    // filterRequest.getEmployedAt() != null ?
    // EmploymentType.valueOf(filterRequest.getEmployedAt()) : null,
    // filterRequest.getProfileImageStatus() != null ?
    // filterRequest.getProfileImageStatus() : "N"
    // );
    // System.out.println("Filtered users: " + users);

    // if (users.isEmpty()) {
    // response.setCode(404);
    // response.setStatus(ResponseStatus.FAILURE);
    // response.setMessage("No users found matching the criteria.");
    // return response;
    // }

    // // Take first 10 users (already shuffled by the query)
    // List<UserEntity> filteredUsers = users.subList(0, Math.min(users.size(),
    // 10));

    // response.setCode(200);
    // response.setStatus(ResponseStatus.SUCCESS);
    // response.setMessage("Filtered users fetched successfully.");
    // response.setData(filteredUsers);
    // } catch (Exception e) {
    // response.setCode(500);
    // response.setStatus(ResponseStatus.FAILURE);
    // response.setMessage("Error fetching filtered users: " + e.getMessage());
    // }
    // return response;
    // }

       public ResultResponse filterUsers(UserFilterRequest filterRequest) {

        ResultResponse response = new ResultResponse();
        List<FilteredUserPlanView> users;

        try {
            if (filterRequest.getUserId() == null) {
                response.setCode(400);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("User Id Is Required");
                return response;
            }

            UserSubscriptions userSubscriptions =
                    userSubscriptionRepository.findTopByUserIdOrderByCreatedAtDesc(filterRequest.getUserId());

            // Free users (no subscription row) can still use basic search

            // 🔹 Normalize all values
            normalizeFilter(filterRequest);

            Integer minAge = parseIntSafe(filterRequest.getMinAge());
            Integer maxAge = parseIntSafe(filterRequest.getMaxAge());
            Integer minIncome = parseIntSafe(filterRequest.getMinAnnualIncome());
            Integer maxIncome = parseIntSafe(filterRequest.getMaxAnnualIncome());

            // 🔹 PREMIUM USER — check ADV_SEARCH feature in plan (Classic+)
            boolean hasAdvSearch = false;
            if (userSubscriptions != null && userSubscriptions.getSubscriptionPlanId() != null && userSubscriptions.getSubscriptionPlanId() != 1L) {
                Features advFeature = featuresRepository.findByCode("ADV_SEARCH");
                if (advFeature != null) {
                    hasAdvSearch = planFeaturesRepository.findByFeatureIdAndSubscriptionPlanId(
                            advFeature.getId(), userSubscriptions.getSubscriptionPlanId()) != null;
                }
            }

            // 🔹 SUBCASTE_SEARCH is a separate, HIGHER gate than ADV_SEARCH (Silver+ vs Classic+),
            // so it gets its own check rather than riding on hasAdvSearch. Which plans grant it is
            // pure data — the planFeatures rows — so changing the tier needs no code change.
            //
            // Enforced server-side as well as in the app because /user/filterUsers can be called
            // directly; a client-only gate would be trivially bypassed. Degrades SILENTLY (the
            // subcaste filter is simply ignored) to match how ADV_SEARCH already behaves, rather
            // than failing the whole search.
            boolean hasSubcasteSearch = false;
            if (userSubscriptions != null && userSubscriptions.getSubscriptionPlanId() != null && userSubscriptions.getSubscriptionPlanId() != 1L) {
                Features subcasteFeature = featuresRepository.findByCode("SUBCASTE_SEARCH");
                if (subcasteFeature != null) {
                    hasSubcasteSearch = planFeaturesRepository.findByFeatureIdAndSubscriptionPlanId(
                            subcasteFeature.getId(), userSubscriptions.getSubscriptionPlanId()) != null;
                }
            }
            List<Long> effectiveSubcasteIds = hasSubcasteSearch ? filterRequest.getSubcasteIds() : null;
            Integer subcasteFilterApplied =
                    (effectiveSubcasteIds != null && !effectiveSubcasteIds.isEmpty()) ? 1 : 0;
            if (hasAdvSearch) {

                users = userRepository.advanceFilter(
                        filterRequest.getGender(),
                        filterRequest.getCasteId(),
                        effectiveSubcasteIds,
                        subcasteFilterApplied,
                        minAge,
                        maxAge,
                        minIncome,
                        maxIncome,
                        filterRequest.getOccupation(),
                        filterRequest.getLocation(),
                        filterRequest.getEmployedAt(),
                        filterRequest.getDegree(),
                        filterRequest.getProfileImageStatus(),
                        filterRequest.getStar(),
                        filterRequest.getDosham(),
                        filterRequest.getProfilesWithHoroscope()
                );

            }
            // 🔹 NORMAL USER — Job Sector was silently dropped here (no employedAt param at
            // all), so a "Government" filter let every sector through. The frontend never
            // gated Job Sector behind a premium check (unlike Education/City), so it's meant
            // to be a basic-tier filter — added it to normalFilter instead of gating the UI.
            else {
                users = userRepository.normalFilter(
                        "Y",
                        filterRequest.getGender(),
                        filterRequest.getCasteId(),
                        effectiveSubcasteIds,
                        subcasteFilterApplied,
                        minAge,
                        maxAge,
                        filterRequest.getLocation(),
                        filterRequest.getEmployedAt(),
                        filterRequest.getProfileImageStatus()
                );
            }

            if (users == null || users.isEmpty()) {
                response.setCode(404);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("No users found matching the criteria.");
                return response;
            }

            // Block filter: remove users blocked by or blocking the caller
            java.util.Set<Long> blockedIds = new java.util.HashSet<>(
                    blockedUserRepository.findBlockAdjacentUserIds(filterRequest.getUserId()));

            List<FilteredUserResponse> filteredUsers = new ArrayList<>();

            for (FilteredUserPlanView u : users) {
                if (blockedIds.contains(u.getUserId())) continue;
                FilteredUserResponse r = new FilteredUserResponse();

                r.setUserId(u.getUserId());
                r.setFirstName(u.getFirstName());
                r.setLastName(u.getLastName() != null ? u.getLastName() : "");
                r.setAge(u.getAge());
                r.setLocation(u.getLocation());
                r.setHeight(u.getHeight());
                r.setProfileImage(u.getProfileImage());
                r.setOccupation(u.getOccupation());
                r.setAnnualIncome(u.getAnnualIncome());
                r.setIsUser(IsUser.valueOf(u.getIsUser()));
                r.setSubscriptionPlanId(u.getSubscriptionPlanId());
                r.setSubscriptionTitle(u.getSubscriptionTitle());
                r.setSubscriptionTag(mapPlanTag(u.getSubscriptionTitle()));
                r.setIdVerified(u.getIdVerified() != null && u.getIdVerified() == 1);
                r.setEducationVerified(u.getEducationVerified() != null && u.getEducationVerified() == 1);
                r.setIncomeVerified(u.getIncomeVerified() != null && u.getIncomeVerified() == 1);
                r.setHasActiveBoost(u.getHasActiveBoost() != null && u.getHasActiveBoost() == 1);

                filteredUsers.add(r);
            }

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Filtered users fetched successfully.");
            response.setData(filteredUsers);

        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching filtered users: " + e.getMessage());
        }

        return response;
    }

// ===================== HELPERS =====================

    private void normalizeFilter(UserFilterRequest req) {

        req.setGender(normalize(req.getGender()));
        req.setLocation(normalize(req.getLocation()));
        req.setProfileImageStatus(normalize(req.getProfileImageStatus()));
        req.setProfilesWithHoroscope(normalize(req.getProfilesWithHoroscope()));

        req.setOccupation(normalizeList(req.getOccupation()));
        req.setEmployedAt(normalizeList(req.getEmployedAt()));
        req.setDegree(normalizeList(req.getDegree()));
        req.setStar(normalizeList(req.getStar()));
        req.setDosham(normalizeList(req.getDosham()));
    }

    // private String normalize(String value) {
    //     if (value == null || value.equalsIgnoreCase("ANY")) {
    //         return null;
    //     }
    //     return value.trim();
    // }

    private <T> List<T> normalizeList(List<T> list) {
        return (list == null || list.isEmpty()) ? null : list;
    }


    private String mapPlanTag(String title) {
        if (title == null)
            return "FREE";

        switch (title.toUpperCase()) {
            case "PLATINUM":
                return "PLATINUM";
            case "GOLD":
                return "GOLD";
            case "SILVER":
                return "SILVER";
            case "BRONZE":
                return "BRONZE";
            default:
                return "FREE";
        }
    }

private String normalize(String val) {
    if (val == null) return null;
    val = val.trim();
    if (val.equalsIgnoreCase("any")) return null;
    if (val.equalsIgnoreCase("all")) return null;
    if (val.equalsIgnoreCase("")) return null;
    return val;
}

private Integer parseIntSafe(String val) {
    try {
        val = normalize(val);
        return val == null ? null : Integer.parseInt(val);
    } catch (Exception e) {
        return null;
    }
}




    public ResultResponse updateUserDetailSection(UserDetailUpdateRequest request) {
        ResultResponse resp = new ResultResponse();
        try {
            UserDetailEntity details = userDetailRepository.findByUserId(request.getUserId());

            if (details == null) {
                resp.setCode(404);
                resp.setStatus(ResponseStatus.FAILURE);
                resp.setMessage("User details not found");
                return resp;
            }

            switch (request.getSection()) {
                case "familyInfo":
                    details.setFamilyInfo(request.getData());
                    break;
                case "astronomicInfo":
                    details.setAstronomicInfo(request.getData());
                    break;
                case "basicInfo":
                    details.setBasicInfo(request.getData());
                    break;
                case "hobbies":
                    details.setHobbies(request.getData());
                    break;
                case "languages":
                    details.setLanguages(request.getData());
                    break;
                default:
                    resp.setCode(400);
                    resp.setStatus(ResponseStatus.FAILURE);
                    resp.setMessage("Invalid section name");
                    return resp;
            }

            userDetailRepository.save(details);

            resp.setCode(200);
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setMessage("User detail section updated successfully");
            return resp;
        } catch (Exception e) {
            resp.setCode(500);
            resp.setStatus(ResponseStatus.FAILURE);
            resp.setMessage("Error updating user detail section: " + e.getMessage());
            return resp;
        }
    }

    @Autowired
    private com.uravugal.matrimony.repositories.BannedIdentifierRepository bannedIdentifierRepository;

    @Autowired
    private com.uravugal.matrimony.repositories.ViewedProfileRepository viewedProfileRepository;

    @Autowired
    private com.uravugal.matrimony.repositories.ContactRevealRepository contactRevealRepository;

    @org.springframework.transaction.annotation.Transactional
    public ResultResponse createUser(UserProfileRequest request) {
        ResultResponse response = new ResultResponse();
        try {
            System.out.println("User request: " + request);

            // Ban blacklist check — mobile or email previously banned
            if (request.getMobileNumber() != null
                    && bannedIdentifierRepository.findByMobile(request.getMobileNumber()) != null) {
                response.setCode(403);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("This mobile number cannot be used to create a new account. Please contact support if you believe this is a mistake.");
                return response;
            }
            if (request.getEmail() != null
                    && bannedIdentifierRepository.findByEmail(request.getEmail()) != null) {
                response.setCode(403);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("This email cannot be used to create a new account. Please contact support if you believe this is a mistake.");
                return response;
            }

            // Subcaste is REQUIRED — but only for castes that actually have one. Castes with a
            // real list (Vanniyar, Naidu, ...) all carry an "Others" row, so a member who doesn't
            // know theirs still has a valid choice and can never dead-end. A caste-level option
            // like FREE CASTE BAR ("any caste is fine") has NO subcastes and must not be blocked —
            // hence the count check rather than a blanket null check. Enforced here rather than
            // with a NOT NULL column, so pre-existing rows without a subcaste stay valid.
            boolean casteHasSubcastes = request.getCasteId() != null
                    && subCasteRepository.countActiveByCasteId(request.getCasteId().longValue()) > 0;
            if (casteHasSubcastes && request.getSubcasteId() == null) {
                response.setCode(400);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("Subcaste is required. Choose 'Others' if you are not sure.");
                return response;
            }

            // When supplied it must belong to the selected caste — otherwise the profile would
            // carry a subcaste from a completely different caste.
            if (request.getSubcasteId() != null) {
                SubCasteEntity subCaste = subCasteRepository.findById(request.getSubcasteId()).orElse(null);
                if (subCaste == null || subCaste.getCasteId() == null || request.getCasteId() == null
                        || !subCaste.getCasteId().equals(request.getCasteId().longValue())) {
                    response.setCode(400);
                    response.setStatus(ResponseStatus.FAILURE);
                    response.setMessage("Subcaste does not belong to the selected caste");
                    return response;
                }
            }

            UserDetailEntity userDetail = new UserDetailEntity();
            UserEntity user = userRepository.findByMobile(request.getMobileNumber());

            if (user != null) {
                response.setCode(401);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("This mobile number is already registered. Please sign in instead.");
                return response;
            }
            if (request.getEmail() != null && !request.getEmail().isBlank()
                    && userRepository.findByEmail(request.getEmail().trim()) != null) {
                response.setCode(409);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("This email is already registered. Please sign in instead.");
                return response;
            }

            // Save user entity
            UserEntity userEntity = new UserEntity();
            userEntity.setFirstName(request.getFirstName());
            userEntity.setLastName(request.getLastName());
            userEntity.setMobile(request.getMobileNumber());
            userEntity.setGender(Gender.valueOf(request.getGender()));
            userEntity.setCasteId(request.getCasteId());
            userEntity.setSubcasteId(request.getSubcasteId());
            // Generate memberId
            String newMemberId = generateMemberId();
            userEntity.setMemberId(newMemberId);
            String encodedPin = Base64.getEncoder().encodeToString(request.getPin().getBytes());
            userEntity.setPin(encodedPin);
            userEntity.setIsUser(IsUser.FA);

            Random random = new Random();
            userEntity.setOtp(1000 + random.nextInt(9000));
            userEntity.setIsBlocked('N');
            userEntity.setLocation(request.getLocation());
            userEntity.setDob(request.getDateOfBirth());
            userEntity.setAge(request.getAge());
            userEntity.setUserStatus(ApprovalStatus.PENDING);
            userEntity.setEmail(request.getEmail());
            userEntity.setViewCount(0);

            userRepository.save(userEntity);

            // Save user detail entity
            userDetail.setUserId(userEntity.getUserId());
            userDetail.setPresentAddress(request.getCurrentAddress());
            userDetail.setDegree(request.getEducation());
            userDetail.setEducationInDetail(request.getEducationInDetail());
            userDetail.setOccupation(request.getOccupation());
            userDetail.setJobPlace(request.getJobPlace());
            if (request.getEmployingIn() != null && !request.getEmployingIn().isBlank()) {
                userDetail.setEmployedAt(EmploymentType.valueOf(request.getEmployingIn().toUpperCase()));
            }
            userDetail.setAnnualIncome(request.getAnnualIncome());
            userDetail.setAbout(generateDefaultAbout(request));

            // ➤ Family Info (If missing fields, default to "")
            Map<String, String> familyInfoMap = new HashMap<>();
            familyInfoMap.put("house", "");
            familyInfoMap.put("father", nullToEmpty(request.getFathersName()));
            familyInfoMap.put("mother", nullToEmpty(request.getMothersName()));
            familyInfoMap.put("father_occupation", nullToEmpty(request.getFathersOccupation()));
            familyInfoMap.put("mother_occupation", nullToEmpty(request.getMothersOccupation()));
            familyInfoMap.put("familyType", "");
            // Set other fields to empty strings since they're not in the request
            familyInfoMap.put("no_of_sister", "");
            familyInfoMap.put("family_status", "");
            familyInfoMap.put("no_of_brother", "");
            familyInfoMap.put("sister_married", "");
            familyInfoMap.put("brother_married", "");

            String familyInfoJson = new ObjectMapper().writeValueAsString(familyInfoMap);
            System.out.println("Family info JSON: ---------------------->" + familyInfoJson);
            userDetail.setFamilyInfo(familyInfoJson);

            // ➤ Basic Info JSON
            Map<String, String> basicInfoMap = new HashMap<>();
            basicInfoMap.put("marital_status", "");
            basicInfoMap.put("place_of_birth", "");
            basicInfoMap.put("mother_language", "");
            basicInfoMap.put("physical_status", "");

            String basicInfoJson = new ObjectMapper().writeValueAsString(basicInfoMap);
            System.out.println("Basic info JSON: ---------------------->" + basicInfoJson);
            userDetail.setBasicInfo(basicInfoJson);

            // ➤ Astro Info JSON (as List of Map)
            Map<String, String> astroInfoMap = new HashMap<>();
            astroInfoMap.put("star", "");
            astroInfoMap.put("dosham", "");
            astroInfoMap.put("moon_sign", "");

            List<Map<String, String>> astroInfoList = new ArrayList<>();
            System.out.println("Astro info JSON: ---------------------->" + astroInfoMap);
            astroInfoList.add(astroInfoMap);

            String astroInfoJson = new ObjectMapper().writeValueAsString(astroInfoList);
            userDetail.setAstronomicInfo(astroInfoJson);

            userDetailRepository.save(userDetail);

            // Auto-assign Free plan (plan ID 1) subscription row
            UserSubscriptions freeSub = new UserSubscriptions();
            freeSub.setUserId(userEntity.getUserId());
            freeSub.setSubscriptionPlanId(1L);
            freeSub.setStatus(com.uravugal.matrimony.enums.SubscriptionStatus.ACTIVE);
            freeSub.setStartDate(java.time.LocalDate.now());
            freeSub.setAutoRenew(com.uravugal.matrimony.enums.ActiveStatus.N);
            userSubscriptionRepository.save(freeSub);

            // Return encoded userId so the frontend can save hobbies
            String base64UserId = Base64.getEncoder().encodeToString(
                    String.valueOf(userEntity.getUserId()).getBytes());
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("userId", base64UserId);

            response.setCode(200);
            response.setMessage("User profile created successfully");
            response.setData(resultData);
            response.setStatus(ResponseStatus.SUCCESS);
            return response;

        } catch (Exception e) {
            // The method swallows exceptions (HTTP-200 pattern), so the transaction
            // must be told to roll back explicitly — otherwise the user row saved
            // above COMMITS and the member gets "failed" with a ghost account
            // created in the background (the reported bug).
            try {
                org.springframework.transaction.interceptor.TransactionAspectSupport
                        .currentTransactionStatus().setRollbackOnly();
            } catch (Exception txIgnore) { }
            response.setCode(500);
            response.setMessage("Error updating user profile: " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
            return response;
        }
    }

    private String generateMemberId() {
        String lastId = userRepository.findLastMemberId();
        int number = 0;

        if (lastId != null && lastId.startsWith(PREFIX)) {
            // Extract numeric part after "UMR"
            number = Integer.parseInt(lastId.substring(PREFIX.length()));
        }

        number++; // if lastId == null → number = 1

        return PREFIX + String.format("%06d", number);
        // e.g., UMR000001, UMR000002 ...
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public ResultResponse updateProfileImage(String userId, MultipartFile file) {
        ResultResponse response = new ResultResponse();
        try {
            // Find the user UserEntity user = userRepository.findById(id).orElse(null);
            Long id = Long.parseLong(userId);
            UserEntity user = userRepository.findById(id).orElse(null);

            if (user == null) {
                response.setCode(404);
                response.setMessage("User not found");
                response.setStatus(ResponseStatus.FAILURE);
                return response;
            }

            if (file != null && !file.isEmpty()) {
                try {
                    // Generate a unique file name
                    String fileName = "profile_" + System.currentTimeMillis() + "_"
                            + UUID.randomUUID().toString().substring(0, 6)
                            + "." + getFileExtension(file.getOriginalFilename());

                    // Create a temporary file
                    File tempFile = File.createTempFile("temp-", fileName);
                    file.transferTo(tempFile);

                    // Upload to S3 — use the returned URL directly (don't reconstruct)
                    String fileUrl = s3UploadService.uploadGalleryImage(tempFile, AWS_BASE_PATH + "user_" + userId);
                    System.out.println("File URL: ------>" + fileUrl);

                    // Update user's profile image URL
                    user.setProfileImage(fileUrl);
                    userRepository.save(user);

                    // Delete the temporary file
                    tempFile.delete();

                    response.setCode(200);
                    response.setMessage("Profile image updated successfully");
                    response.setStatus(ResponseStatus.SUCCESS);
                    response.setData(fileUrl);
                } catch (IOException e) {
                    response.setCode(500);
                    response.setMessage("Error processing image: " + e.getMessage());
                    response.setStatus(ResponseStatus.FAILURE);
                }
            } else {
                response.setCode(400);
                response.setMessage("No file provided");
                response.setStatus(ResponseStatus.FAILURE);
            }
        } catch (NumberFormatException e) {
            response.setCode(400);
            response.setMessage("Invalid user ID format");
            response.setStatus(ResponseStatus.FAILURE);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Error updating profile image: " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    private String getFileExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int lastDot = fileName.lastIndexOf('.');
        return lastDot == -1 ? "" : fileName.substring(lastDot + 1);
    }

    public String generateDefaultAbout(UserProfileRequest user) {
        String fullName = (user.getFirstName() + " " + user.getLastName()).trim();

        String intro;
        if ("Male".equalsIgnoreCase(user.getGender())) {
            intro = "Hi, I'm " + fullName + ", an ambitious and positive person";
        } else if ("Female".equalsIgnoreCase(user.getGender())) {
            intro = "Hello, I'm " + fullName + ", a confident and caring person";
        } else {
            intro = "Hi, I'm " + fullName + ", a driven and friendly person";
        }

        StringBuilder about = new StringBuilder(intro);

        if (user.getOccupation() != null && !user.getOccupation().isEmpty()) {
            about.append(", working as a ").append(user.getOccupation());
        }

        if (user.getNativePlace() != null && !user.getNativePlace().isEmpty()) {
            about.append(" from ").append(user.getNativePlace());
        }

        about.append(". Looking forward to a happy future together.");

        // Ensure it fits in 255 chars
        if (about.length() > 255) {
            return about.substring(0, 252) + "...";
        }

        return about.toString();
    }

    public ResultResponse login(UserEntity user) {
        ResultResponse finalresult = new ResultResponse();
        HashMap<String, Object> returnValue = new HashMap<>();

        try {
            UserEntity userentity = null;
            if (user.getMobile() != null) {
                // Email-or-mobile login (2026-09-05): the `mobile` field carries either
                // identifier — anything containing '@' is looked up by email, everything
                // else stays on the mobile path. The family-login fall-through is
                // mobile-only by design (family PINs are provisioned against a mobile).
                String identifier = user.getMobile().trim();
                if (identifier.contains("@")) {
                    userentity = userRepository.findByEmail(identifier);
                    if (userentity == null) {
                        finalresult.setMessage("User Not Found");
                        finalresult.setStatus(ResponseStatus.FAILURE);
                        finalresult.setCode(404);
                        return finalresult;
                    }
                } else {
                    userentity = userRepository.findByMobile(identifier);
                    if (userentity == null) {
                        // Fall through: try family login
                        Map<String, Object> familyPayload = familyLoginService.tryLogin(identifier, user.getPin());
                        if (familyPayload != null) {
                            finalresult.setData(familyPayload);
                            finalresult.setMessage("Family Login Success");
                            finalresult.setStatus(ResponseStatus.SUCCESS);
                            finalresult.setCode(200);
                            return finalresult;
                        }
                        finalresult.setMessage("User Not Found");
                        finalresult.setStatus(ResponseStatus.FAILURE);
                        finalresult.setCode(404);
                        return finalresult;
                    }
                }
            } else {
                finalresult.setMessage("Email or Mobile Number Required");
                finalresult.setStatus(ResponseStatus.FAILURE);
                finalresult.setCode(404);
                return finalresult;
            }

            if (user.getPin() != null && (userentity.getPin() == null || userentity.getPin().equals(""))) {
                finalresult.setMessage("Pin Not Setted.");
                finalresult.setStatus(ResponseStatus.FAILURE);
                finalresult.setCode(204);
                return finalresult;
            }

            // Suspension / ban check — block login if account is currently suspended or banned
            if (userentity.getUserStatus() == com.uravugal.matrimony.enums.ApprovalStatus.BANNED) {
                finalresult.setMessage("Your account has been permanently banned for violating our community guidelines. Please contact support.");
                finalresult.setStatus(ResponseStatus.FAILURE);
                finalresult.setCode(403);
                return finalresult;
            }
            if (userentity.getUserStatus() == com.uravugal.matrimony.enums.ApprovalStatus.SUSPENDED) {
                if (userentity.getSuspendedUntil() != null
                        && userentity.getSuspendedUntil().isAfter(LocalDateTime.now())) {
                    String until = userentity.getSuspendedUntil().toLocalDate().toString();
                    finalresult.setMessage("Your account is suspended until " + until + ". Please contact support.");
                    finalresult.setStatus(ResponseStatus.FAILURE);
                    finalresult.setCode(403);
                    return finalresult;
                } else {
                    // Auto-unsuspend — suspension window expired
                    userentity.setUserStatus(com.uravugal.matrimony.enums.ApprovalStatus.APPROVED);
                    userentity.setSuspendedUntil(null);
                    userRepository.save(userentity);
                }
            }

            // Approval gate — a newly registered profile (from any client: mobile app, website,
            // admin-created) defaults to PENDING and must not be able to log in anywhere until an
            // admin approves it. Previously this was enforced ONLY client-side (mobile app read
            // `userStatus` from a successful 200 login and redirected to a "pending" screen) —
            // meaning login itself always succeeded and issued a real, usable token regardless of
            // approval status. Any other client (e.g. a website login) that didn't reimplement that
            // same client-side check would let PENDING/REJECTED users straight in. Blocking here
            // instead makes the gate apply uniformly no matter which client calls this endpoint.
            if (userentity.getUserStatus() == com.uravugal.matrimony.enums.ApprovalStatus.PENDING
                    || userentity.getUserStatus() == com.uravugal.matrimony.enums.ApprovalStatus.REJECTED) {
                boolean isRejected = userentity.getUserStatus() == com.uravugal.matrimony.enums.ApprovalStatus.REJECTED;
                String pendingBase64Id = Base64.getEncoder()
                        .encodeToString(String.valueOf(userentity.getUserId()).getBytes());
                HashMap<String, Object> pendingData = new HashMap<>();
                pendingData.put("userId", pendingBase64Id);
                pendingData.put("firstName", userentity.getFirstName());
                pendingData.put("lastName", userentity.getLastName());
                pendingData.put("userStatus", userentity.getUserStatus().toString());
                pendingData.put("rejectionReason", userentity.getRejectionReason());
                finalresult.setData(pendingData);
                finalresult.setMessage(isRejected
                        ? "Your profile was not approved" + (userentity.getRejectionReason() != null
                                ? ": " + userentity.getRejectionReason() : ".")
                        : "Your profile is still under review. You'll be able to log in once an admin approves it.");
                finalresult.setStatus(ResponseStatus.FAILURE);
                finalresult.setCode(403);
                return finalresult;
            }

            // PIN format can be either legacy base64 OR BCrypt (after password reset).
            // BCrypt strings start with $2a$, $2b$, or $2y$.
            boolean pinMatches;
            String storedPin = userentity.getPin();
            if (storedPin != null && storedPin.startsWith("$2")) {
                // BCrypt hash
                pinMatches = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                        .matches(user.getPin(), storedPin);
            } else {
                // Legacy base64 encoding
                try {
                    String decodedValue = new String(Base64.getDecoder().decode(storedPin));
                    pinMatches = decodedValue.equals(user.getPin());
                } catch (IllegalArgumentException ex) {
                    pinMatches = false;
                }
            }

            if (pinMatches) {
                String valueBfr = String.valueOf(userentity.getUserId());
                String base64String = Base64.getEncoder().encodeToString(valueBfr.getBytes());

                // Issue JWT access + refresh tokens
                String accessToken = jwtUtil.generateToken(
                        userentity.getUserId(),
                        userentity.getMobile(),
                        userentity.getIsUser() != null ? userentity.getIsUser().name() : "FA"
                );
                String refreshToken = jwtUtil.generateRefreshToken(userentity.getUserId());
                userentity.setRefreshToken(refreshToken);
                userentity.setRefreshTokenExpiry(LocalDateTime.now().plusDays(60));
                userRepository.save(userentity);

                returnValue.put("userId", base64String);
                returnValue.put("email", userentity.getEmail());
                returnValue.put("gender", userentity.getGender().name());
                returnValue.put("dob", userentity.getDob().toString());
                returnValue.put("location", userentity.getLocation());
                returnValue.put("mobile", userentity.getMobile());
                returnValue.put("firstName", userentity.getFirstName());
                returnValue.put("lastName", userentity.getLastName());
                returnValue.put("profileImage", userentity.getProfileImage());
                returnValue.put("casteId", userentity.getCasteId());
                returnValue.put("isUser", userentity.getIsUser().name());
                returnValue.put("userStatus", userentity.getUserStatus() != null
                        ? userentity.getUserStatus().toString() : "APPROVED");
                returnValue.put("rejectionReason", userentity.getRejectionReason());
                returnValue.put("token", accessToken);
                returnValue.put("refreshToken", refreshToken);

                finalresult.setData(returnValue);
                finalresult.setMessage("Login Success");
                finalresult.setStatus(ResponseStatus.SUCCESS);
                finalresult.setCode(200);
            } else {
                // PIN mismatch on primary user — last chance: try family login
                Map<String, Object> familyPayload = familyLoginService.tryLogin(user.getMobile(), user.getPin());
                if (familyPayload != null) {
                    finalresult.setData(familyPayload);
                    finalresult.setMessage("Family Login Success");
                    finalresult.setStatus(ResponseStatus.SUCCESS);
                    finalresult.setCode(200);
                    return finalresult;
                }
                finalresult.setMessage("Login Failed");
                finalresult.setStatus(ResponseStatus.FAILURE);
                finalresult.setCode(401);
            }

        } catch (Exception e) {
            finalresult.setStatus(ResponseStatus.FAILURE);
            finalresult.setMessage(e.getMessage());
            finalresult.setCode(500);
        }
        return finalresult;
    }

    /** astroInfo[0].birth_time, or null. Tolerates the array/object shapes both signup paths write. */
    public String readBirthTime(UserDetailEntity detail) {
        if (detail == null || detail.getAstronomicInfo() == null) return null;
        try {
            com.fasterxml.jackson.databind.JsonNode node =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(detail.getAstronomicInfo());
            if (node.isArray()) node = node.size() > 0 ? node.get(0) : null;
            if (node == null) return null;
            com.fasterxml.jackson.databind.JsonNode bt = node.get("birth_time");
            if (bt == null || bt.isNull()) return null;
            String v = bt.asText().trim();
            return (v.isEmpty() || "null".equalsIgnoreCase(v)) ? null : v;
        } catch (Exception e) {
            return null;
        }
    }

    /** Blank astroInfo[0].birth_time on an entity that is about to be serialised to a viewer. */
    private void stripBirthTime(UserDetailEntity detail) {
        if (detail == null || detail.getAstronomicInfo() == null) return;
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode node = om.readTree(detail.getAstronomicInfo());
            com.fasterxml.jackson.databind.JsonNode target = node.isArray() ? (node.size() > 0 ? node.get(0) : null) : node;
            if (target == null || !target.isObject()) return;
            ((com.fasterxml.jackson.databind.node.ObjectNode) target).put("birth_time", "");
            detail.setAstronomicInfo(om.writeValueAsString(node));
        } catch (Exception ignored) { }
    }

    public void updateLastSeen(Long userId, boolean isOnline) {
        Optional<UserEntity> optionalUser = userRepository.findById(userId);
        if (optionalUser.isPresent()) {
            UserEntity user = optionalUser.get();
            user.setOnline(isOnline);
            user.setLastSeen(LocalDateTime.now());
            userRepository.save(user);
        }
    }

    /**
     * HARD DELETE user account + all related rows. Apple App Store and Google Play both
     * require real deletion. Wipes every child table referencing the user, then the users row.
     *
     * \u1F534 This was completely broken in production until 2026-08-18: ten of the column
     * names below were wrong (interestReceive, chats.receiverId, conversations.userOne/userTwo,
     * viewedProfile.viewerUserId/viewedUserId, userLikes.likedUserId, and the four snake_case
     * ones), so every call ended in a 500 and deleted NOTHING.
     *
     * The old per-statement try/catch is what hid it. Catching a JDBC exception does NOT
     * un-poison the surrounding transaction \u2014 Spring has already marked it rollback-only,
     * so the commit throws "Transaction silently rolled back" and every successful delete in
     * the batch is undone. A swallowed error inside a transaction is not resilience, it is a
     * silent total failure. The cascade is now atomic on purpose: account deletion must be
     * all-or-nothing, since a partial wipe leaves orphaned chats and interests pointing at a
     * user row that no longer exists.
     *
     * Order matters: chats are removed before conversations, because the second chats sweep
     * resolves conversation ids via a subquery against that table.
     */
    @org.springframework.transaction.annotation.Transactional
    public ResultResponse deleteAccount(String encodedUserId, String reason) {
        ResultResponse resp = new ResultResponse();
        try {
            Long userId = Long.parseLong(new String(Base64.getDecoder().decode(encodedUserId)));

            // Snapshot the member BEFORE the wipe (their rows are about to go),
            // but only persist it AFTER the wipe succeeds — a deleted_accounts
            // row must mean the deletion actually happened.
            com.uravugal.matrimony.models.DeletedAccount tombstone = null;
            UserEntity doomed = userRepository.findById(userId).orElse(null);
            if (doomed != null) {
                tombstone = new com.uravugal.matrimony.models.DeletedAccount();
                tombstone.setUserId(userId);
                tombstone.setMemberId(doomed.getMemberId());
                tombstone.setFirstName(doomed.getFirstName());
                tombstone.setLastName(doomed.getLastName());
                tombstone.setGender(doomed.getGender() != null ? doomed.getGender().name() : null);
                tombstone.setMobile(doomed.getMobile());
                tombstone.setEmail(doomed.getEmail());
                tombstone.setCasteId(doomed.getCasteId());
                if (reason != null) {
                    reason = reason.trim();
                    if (reason.length() > 500) reason = reason.substring(0, 500);
                    if (reason.isEmpty()) reason = null;
                }
                tombstone.setReason(reason);
                // Which plan they held at deletion — latest subscription row's title.
                try {
                    java.util.List<?> pt = entityManager.createNativeQuery(
                            "SELECT sp.title FROM user_subscriptions us JOIN subscription_plans sp "
                            + "ON sp.id = us.subscriptionPlanId WHERE us.userId = :id "
                            + "ORDER BY us.createdAt DESC LIMIT 1")
                            .setParameter("id", userId).getResultList();
                    tombstone.setPlanTitle(!pt.isEmpty() && pt.get(0) != null ? pt.get(0).toString() : "Free");
                } catch (Exception planEx) {
                    tombstone.setPlanTitle("Free");
                }
            }

            // Child tables — every column name here is verified against the live schema.
            String[] wipes = new String[] {
                "DELETE FROM user_feature_usage WHERE userId = :id",
                "DELETE FROM family_logins WHERE primaryUserId = :id",
                "DELETE FROM service_requests WHERE userId = :id",
                "DELETE FROM user_subscriptions WHERE userId = :id",
                "DELETE FROM shortlisted_profiles WHERE shortlistedBy = :id OR shortlistedUserId = :id",
                "DELETE FROM restricted_field_requests WHERE requestedBy = :id OR requestedTo = :id",
                "DELETE FROM interestRequest WHERE interestSend = :id OR interestReceived = :id",
                // chats has no receiverId — a message is addressed by its conversation. Sweep the
                // user's own messages first, then everything left in any conversation they were in
                // (i.e. the other participant's side), before the conversations themselves go.
                "DELETE FROM chats WHERE senderId = :id",
                "DELETE FROM chats WHERE conversationId IN "
                    + "(SELECT id FROM conversations WHERE user_one_id = :id OR user_two_id = :id)",
                "DELETE FROM conversations WHERE user_one_id = :id OR user_two_id = :id",
                "DELETE FROM notifications WHERE senderId = :id OR receiverId = :id",
                "DELETE FROM UserDeviceInformation WHERE userId = :id",
                "DELETE FROM viewedProfile WHERE viewedBy = :id OR userIdValue = :id",
                "DELETE FROM hidden_fields WHERE userId = :id",
                "DELETE FROM blockedUsers WHERE blockedByUserId = :id OR blockedUserId = :id",
                "DELETE FROM userLikes WHERE likedBy = :id OR likedTo = :id",
                "DELETE FROM userReports WHERE reportedByUserId = :id OR reportedUserId = :id",
                "DELETE FROM savedSearchFilters WHERE saved_search_id IN "
                    + "(SELECT id FROM savedSearches WHERE user_id = :id)",
                "DELETE FROM savedSearches WHERE user_id = :id",
                "DELETE FROM user_connections WHERE followerId = :id OR followingId = :id",
                "DELETE FROM PAYMENT_REQUESTS WHERE user_id = :id",
                // Verification documents. These were MISSING until 2026-08-18 — a member could
                // "permanently delete" their account and their Aadhaar/PAN/income/education
                // document rows survived, which contradicts both the privacy policy and Google
                // Play's requirement that associated data be deleted. ⚠️ These rows hold a
                // document_url pointing at object storage; see deleteAccountStorageNote below.
                "DELETE FROM id_verifications WHERE userId = :id",
                "DELETE FROM income_verifications WHERE userId = :id",
                "DELETE FROM education_verifications WHERE userId = :id",
                "DELETE FROM profile_boosts WHERE userId = :id",
                "DELETE FROM callback_requests WHERE userId = :id",
                // Support conversations: messages first, then the tickets they hang off.
                "DELETE FROM ticket_messages WHERE ticket_id IN "
                    + "(SELECT id FROM support_tickets WHERE user_id = :id)",
                "DELETE FROM support_tickets WHERE user_id = :id",
                "DELETE FROM gallery WHERE userId = :id",
                "DELETE FROM user_details WHERE userId = :id",
                "DELETE FROM users WHERE userId = :id"
            };

            int totalDeleted = 0;
            for (String sql : wipes) {
                // No per-statement catch: see the method javadoc. A failure here must abort the
                // whole delete and surface, not be logged and stepped over.
                int n = entityManager.createNativeQuery(sql)
                        .setParameter("id", userId)
                        .executeUpdate();
                totalDeleted += n;
                System.out.println("deleteAccount [" + n + "] " + sql);
            }

            // Wipe succeeded — record the tombstone. Never fail the deletion over
            // it (the account IS gone); just log if the insert itself fails.
            if (tombstone != null) {
                try {
                    deletedAccountRepository.save(tombstone);
                } catch (Exception tombEx) {
                    System.out.println("Warning: deleted_accounts insert failed for user " + userId + ": " + tombEx.getMessage());
                }
            }

            resp.setCode(200);
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setMessage("Account permanently deleted.");
            resp.setData(totalDeleted);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setStatus(ResponseStatus.FAILURE);
            resp.setMessage("Error deleting account: " + e.getMessage());
        }
        return resp;
    }

    public ResultResponse getUserPaidStatus(String encodedUserId) {
        ResultResponse response = new ResultResponse();
        try {
            Long userId = Long.parseLong(new String(Base64.getDecoder().decode(encodedUserId)));
            Optional<UserEntity> user = userRepository.findById(userId);
            if (user.isPresent()) {
                boolean isPU = user.get().getIsUser() == IsUser.PU;
                response.setCode(200);
                response.setStatus(ResponseStatus.SUCCESS);
                response.setMessage("User status retrieved successfully");
                response.setData(isPU);
            } else {
                response.setCode(404);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("User not found");
            }
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error retrieving user status: " + e.getMessage());
        }
        return response;
    }

    /**
     * Reveal contact info (mobile + email) for a profile.
     * Deducts 1 from VIEW_PERSONAL_INFO quota for plans with numeric limits (Classic = 40).
     * Returns the contact info if allowed.
     */
    public ResultResponse revealContact(String viewerEncodedId, Long profileUserId) {
        ResultResponse response = new ResultResponse();
        try {
            Long parsedId;
            try {
                parsedId = Long.parseLong(new String(Base64.getDecoder().decode(viewerEncodedId)));
            } catch (Exception e) {
                parsedId = Long.parseLong(viewerEncodedId);
            }
            final Long viewerId = parsedId;

            // Can't reveal own contact
            if (viewerId.equals(profileUserId)) {
                response.setCode(400);
                response.setMessage("Cannot reveal own contact");
                response.setStatus(ResponseStatus.FAILURE);
                return response;
            }

            // CONSENT GATE — a paid plan buys the ABILITY to see contact details, never the right
            // to see a specific person's. The other member must have accepted an interest request
            // first, exactly as already required for Voice Call, Star Match and horoscope.
            // Checked BEFORE the quota logic so a blocked attempt never burns a reveal.
            Optional<InterestRequest> contactInterest =
                    interestRequestRepository.findBetweenUsers(viewerId, profileUserId);
            if (contactInterest.isEmpty()
                    || contactInterest.get().getAcceptStatus() != ApprovalStatus.APPROVED) {
                response.setCode(403);
                response.setMessage("INTEREST_NOT_APPROVED");
                response.setStatus(ResponseStatus.FAILURE);
                return response;
            }

            // Get viewer's subscription
            UserSubscriptions viewerSub = userSubscriptionRepository.findTopByUserIdOrderByCreatedAtDesc(viewerId);
            if (viewerSub == null || viewerSub.getSubscriptionPlanId() == 1L) {
                response.setCode(403);
                response.setMessage("PLAN_UPGRADE_REQUIRED");
                response.setStatus(ResponseStatus.FAILURE);
                return response;
            }

            Long planId = viewerSub.getSubscriptionPlanId();
            Features personalInfo = featuresRepository.findByCode("VIEW_PERSONAL_INFO");
            if (personalInfo == null) {
                response.setCode(500);
                response.setMessage("Feature not configured");
                response.setStatus(ResponseStatus.FAILURE);
                return response;
            }

            PlanFeatures pf = planFeaturesRepository.findByFeatureIdAndSubscriptionPlanId(
                    personalInfo.getId(), planId);
            if (pf == null) {
                response.setCode(403);
                response.setMessage("PLAN_UPGRADE_REQUIRED");
                response.setStatus(ResponseStatus.FAILURE);
                return response;
            }

            String limitVal = pf.getLimitValue();
            boolean isUnlimited = "enabled".equalsIgnoreCase(limitVal) || "unlimited".equalsIgnoreCase(limitVal);

            if (!isUnlimited) {
                // Numeric limit — track usage
                int limit = Integer.parseInt(limitVal);
                com.uravugal.matrimony.models.UserFeatureUsage usage = userFeatureUsageRepository
                        .findByUserIdAndSubscriptionIdAndFeatureId(viewerId, viewerSub.getId(), personalInfo.getId())
                        .orElseGet(() -> {
                            com.uravugal.matrimony.models.UserFeatureUsage u = new com.uravugal.matrimony.models.UserFeatureUsage();
                            u.setUserId(viewerId);
                            u.setSubscriptionId(viewerSub.getId());
                            u.setFeatureId(personalInfo.getId());
                            u.setUsedCount(0);
                            return u;
                        });

                if (usage.getUsedCount() >= limit) {
                    response.setCode(403);
                    response.setMessage("CONTACT_VIEW_LIMIT_EXCEEDED");
                    response.setData(java.util.Map.of("limit", limit, "used", usage.getUsedCount()));
                    response.setStatus(ResponseStatus.FAILURE);
                    return response;
                }

                // Only increment if first time revealing this profile's contact
                boolean alreadyRevealed = contactRevealRepository.existsByViewerIdAndRevealedUserId(viewerId, profileUserId);
                if (!alreadyRevealed) {
                    usage.setUsedCount(usage.getUsedCount() + 1);
                    userFeatureUsageRepository.save(usage);

                    // Record the reveal
                    com.uravugal.matrimony.models.ContactReveal reveal = new com.uravugal.matrimony.models.ContactReveal();
                    reveal.setViewerId(viewerId);
                    reveal.setRevealedUserId(profileUserId);
                    contactRevealRepository.save(reveal);
                }
            }

            // Fetch the profile user's contact info
            Optional<UserEntity> profileOpt = userRepository.findById(profileUserId);
            if (!profileOpt.isPresent()) {
                response.setCode(404);
                response.setMessage("Profile not found");
                response.setStatus(ResponseStatus.FAILURE);
                return response;
            }

            UserEntity profileUser = profileOpt.get();
            Map<String, Object> contactData = new HashMap<>();
            contactData.put("mobile", profileUser.getMobile());
            contactData.put("email", profileUser.getEmail());
            contactData.put("userId", profileUserId);
            contactData.put("unlimited", isUnlimited);
            if (!isUnlimited) {
                int limit = Integer.parseInt(pf.getLimitValue());
                com.uravugal.matrimony.models.UserFeatureUsage currentUsage = userFeatureUsageRepository
                        .findByUserIdAndSubscriptionIdAndFeatureId(viewerId, viewerSub.getId(), personalInfo.getId())
                        .orElse(null);
                int used = currentUsage != null ? currentUsage.getUsedCount() : 0;
                contactData.put("remaining", Math.max(0, limit - used));
                contactData.put("total", limit);
            }

            // "Reveal Contact" now reveals horoscope in the same tap — one combined action
            // instead of two separate, disconnected checks. This does NOT bypass horoscope's own
            // access rules (plan must have HOROSCOPE_VIEW, and the interest between the two users
            // must be APPROVED) — it only shows it if the viewer was already entitled to see it,
            // just surfaced here alongside the contact info rather than only on a passive wait.
            boolean horoscopeEligible = false;
            Features horoscopeFeature = featuresRepository.findByCode("HOROSCOPE_VIEW");
            if (horoscopeFeature != null && planFeaturesRepository.findByFeatureIdAndSubscriptionPlanId(horoscopeFeature.getId(), planId) != null) {
                Optional<InterestRequest> interestOpt = interestRequestRepository.findBetweenUsers(viewerId, profileUserId);
                horoscopeEligible = interestOpt.isPresent() && interestOpt.get().getAcceptStatus() == ApprovalStatus.APPROVED;
            }
            contactData.put("horoscopeEligible", horoscopeEligible);
            if (horoscopeEligible) {
                UserDetailEntity profileDetail = userDetailRepository.findByUserId(profileUserId);
                String horoscope = profileDetail != null ? profileDetail.getHoroscope() : null;
                contactData.put("horoscope", (horoscope != null && !horoscope.equals("null")) ? horoscope : null);
                // Birth time rides with the horoscope: same astro data, same consent rule
                // (plan has HOROSCOPE_VIEW and the interest is APPROVED). Masked otherwise —
                // see the masking block in getProfileDetailWithIntractionStatus.
                contactData.put("birthTime", readBirthTime(profileDetail));
            }

            response.setCode(200);
            response.setMessage("Contact revealed successfully");
            response.setData(contactData);
            response.setStatus(ResponseStatus.SUCCESS);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Error: " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    /**
     * Read-only lookup of the current user's own VIEW_PERSONAL_INFO quota — same numbers
     * revealContact tracks, but without spending a reveal or targeting any specific profile.
     * Lets the Profile tab show "12 of 40 contact reveals used" proactively instead of the count
     * only ever surfacing after already using one on some profile's Contact card.
     */
    public ResultResponse getContactRevealStatus(String viewerEncodedId) {
        ResultResponse response = new ResultResponse();
        try {
            Long viewerId;
            try {
                viewerId = Long.parseLong(new String(Base64.getDecoder().decode(viewerEncodedId)));
            } catch (Exception e) {
                viewerId = Long.parseLong(viewerEncodedId);
            }

            Map<String, Object> status = new HashMap<>();

            UserSubscriptions viewerSub = userSubscriptionRepository.findTopByUserIdOrderByCreatedAtDesc(viewerId);
            Features personalInfo = featuresRepository.findByCode("VIEW_PERSONAL_INFO");
            PlanFeatures pf = (viewerSub != null && personalInfo != null)
                    ? planFeaturesRepository.findByFeatureIdAndSubscriptionPlanId(personalInfo.getId(), viewerSub.getSubscriptionPlanId())
                    : null;

            if (viewerSub == null || viewerSub.getSubscriptionPlanId() == 1L || pf == null) {
                // Free plan, or current plan has no VIEW_PERSONAL_INFO row at all — nothing to show.
                status.put("applicable", false);
                response.setCode(200);
                response.setStatus(ResponseStatus.SUCCESS);
                response.setData(status);
                return response;
            }

            String limitVal = pf.getLimitValue();
            boolean isUnlimited = "enabled".equalsIgnoreCase(limitVal) || "unlimited".equalsIgnoreCase(limitVal);
            status.put("applicable", true);
            status.put("unlimited", isUnlimited);

            if (!isUnlimited) {
                int limit = Integer.parseInt(limitVal);
                int used = userFeatureUsageRepository
                        .findByUserIdAndSubscriptionIdAndFeatureId(viewerId, viewerSub.getId(), personalInfo.getId())
                        .map(com.uravugal.matrimony.models.UserFeatureUsage::getUsedCount)
                        .orElse(0);
                status.put("used", used);
                status.put("total", limit);
                status.put("remaining", Math.max(0, limit - used));
            }

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setData(status);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Error fetching contact reveal status: " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    /**
     * List of profiles whose contact info this viewer has revealed — lets the Profile tab's
     * "Contact Reveals" card show who those reveals actually went to, not just a bare count.
     */
    public ResultResponse getRevealedContacts(String viewerEncodedId) {
        ResultResponse response = new ResultResponse();
        try {
            Long viewerId;
            try {
                viewerId = Long.parseLong(new String(Base64.getDecoder().decode(viewerEncodedId)));
            } catch (Exception e) {
                viewerId = Long.parseLong(viewerEncodedId);
            }

            List<com.uravugal.matrimony.models.ContactReveal> reveals = contactRevealRepository
                    .findByViewerIdOrderByCreatedAtDesc(viewerId);

            List<com.uravugal.matrimony.dtos.RevealedContactDTO> result = new ArrayList<>();
            for (com.uravugal.matrimony.models.ContactReveal reveal : reveals) {
                UserEntity user = userRepository.findById(reveal.getRevealedUserId()).orElse(null);
                if (user == null) continue;

                com.uravugal.matrimony.dtos.RevealedContactDTO dto = new com.uravugal.matrimony.dtos.RevealedContactDTO();
                dto.setUserId(user.getUserId());
                dto.setMemberId(user.getMemberId());
                dto.setFirstName(user.getFirstName());
                dto.setLastName(user.getLastName());
                dto.setGender(user.getGender() != null ? user.getGender().name() : null);
                dto.setMobile(user.getMobile());
                dto.setEmail(user.getEmail());
                dto.setProfileImage(user.getProfileImage());
                dto.setRevealedAt(reveal.getCreatedAt() != null
                        ? java.sql.Timestamp.valueOf(reveal.getCreatedAt())
                        : null);

                if (user.getDob() != null) {
                    dto.setAge((int) java.time.Period.between(user.getDob(), java.time.LocalDate.now()).getYears());
                }
                if (user.getUserDetail() != null && !user.getUserDetail().isEmpty()) {
                    UserDetailEntity detail = user.getUserDetail().get(0);
                    dto.setOccupation(detail.getOccupation());
                    dto.setLocation(detail.getPresentAddress());
                    dto.setDegree(detail.getDegree());
                    dto.setAnnualIncome(detail.getAnnualIncome());
                }

                result.add(dto);
            }

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Revealed contacts fetched successfully");
            response.setData(result);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Error fetching revealed contacts: " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    /**
     * Interest-based matches — same pattern as getDailyShuffledUsersByCaste.
     * Returns List<UserEntity> filtered by caste + opposite gender + isActive + shared hobbies.
     */
    public ResultResponse getInterestMatches(Integer casteId, Gender gender, String encodedUserId) {
        ResultResponse response = new ResultResponse();
        try {
            // Decode viewer's userId to load their hobbies
            Long viewerId = Long.parseLong(new String(java.util.Base64.getDecoder().decode(encodedUserId)));
            UserDetailEntity viewerUd = userDetailRepository.findByUserId(viewerId);
            java.util.List<String> viewerHobbies = new java.util.ArrayList<>();
            if (viewerUd != null && viewerUd.getHobbies() != null && !viewerUd.getHobbies().isBlank()) {
                try {
                    viewerHobbies = new com.fasterxml.jackson.databind.ObjectMapper()
                            .readValue(viewerUd.getHobbies(), new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {});
                } catch (Exception ignored) {}
            }

            if (viewerHobbies.isEmpty()) {
                response.setCode(200);
                response.setStatus(ResponseStatus.SUCCESS);
                response.setMessage("No hobbies set — add interests first");
                response.setData(java.util.Collections.emptyList());
                return response;
            }

            // Fetch all active users of opposite gender + same caste (same as daily shuffle)
            List<UserEntity> allUsers = userRepository.findAllByCasteIdAndGenderAndIsActiveAndIsUserNot(
                    casteId, gender, ActiveStatus.Y, IsUser.ADM);

            // Filter: keep only profiles whose hobbies overlap with viewer's hobbies
            final java.util.Set<String> viewerHobbySet = new java.util.HashSet<>(viewerHobbies);
            java.util.Set<Long> blockedIds = new java.util.HashSet<>(blockedUserRepository.findBlockAdjacentUserIds(viewerId));
            java.util.List<UserEntity> matched = new java.util.ArrayList<>();
            for (UserEntity u : allUsers) {
                if (u.getUserId().equals(viewerId)) continue;
                if (blockedIds.contains(u.getUserId())) continue;
                UserDetailEntity ud = userDetailRepository.findByUserId(u.getUserId());
                if (ud == null || ud.getHobbies() == null || ud.getHobbies().isBlank()) continue;
                try {
                    java.util.List<String> theirHobbies = new com.fasterxml.jackson.databind.ObjectMapper()
                            .readValue(ud.getHobbies(), new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {});
                    boolean hasOverlap = theirHobbies.stream().anyMatch(viewerHobbySet::contains);
                    if (hasOverlap) matched.add(u);
                } catch (Exception ignored) {}
            }

            // Shuffle for variety
            long seed = java.time.LocalDate.now().toEpochDay() + viewerId;
            java.util.Collections.shuffle(matched, new java.util.Random(seed));
            matched = matched.subList(0, Math.min(matched.size(), 30));

            if (!matched.isEmpty()) {
                response.setCode(200);
                response.setStatus(ResponseStatus.SUCCESS);
                response.setMessage("Interest matches fetched successfully.");
                response.setData(matched);
            } else {
                response.setCode(200);
                response.setStatus(ResponseStatus.SUCCESS);
                response.setMessage("No matching profiles found with shared interests.");
                response.setData(java.util.Collections.emptyList());
            }
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error: " + e.getMessage());
        }
        return response;
    }

    /**
     * Interest-based matches — SQL-driven (legacy, kept for /user-details endpoint).
     */
    public ResultResponse getInterestMatchesSQL(String encodedUserId) {
        ResultResponse resp = new ResultResponse();
        try {
            Long userId = Long.parseLong(new String(java.util.Base64.getDecoder().decode(encodedUserId)));
            UserEntity viewer = userRepository.findById(userId).orElse(null);
            if (viewer == null || viewer.getCasteId() == null || viewer.getGender() == null) {
                resp.setCode(404);
                resp.setStatus(ResponseStatus.FAILURE);
                resp.setMessage("User not found or incomplete profile");
                return resp;
            }

            // Load viewer's hobbies
            com.uravugal.matrimony.models.UserDetailEntity viewerUd =
                    userDetailRepository.findByUserId(userId);
            String hobbiesJson = viewerUd != null ? viewerUd.getHobbies() : null;
            java.util.List<String> hobbies = new java.util.ArrayList<>();
            if (hobbiesJson != null && !hobbiesJson.isBlank()) {
                try {
                    hobbies = new com.fasterxml.jackson.databind.ObjectMapper()
                            .readValue(hobbiesJson, new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {});
                } catch (Exception ignored) {}
            }
            if (hobbies.isEmpty()) {
                resp.setCode(200);
                resp.setStatus(ResponseStatus.SUCCESS);
                resp.setMessage("No hobbies set");
                resp.setData(java.util.Collections.emptyList());
                return resp;
            }

            // Pad hobbies to 15 slots (the query expects h0..h14 params)
            String[] h = new String[15];
            for (int i = 0; i < 15; i++) {
                h[i] = i < hobbies.size() ? hobbies.get(i) : null;
            }

            Gender oppositeGender = viewer.getGender() == Gender.M ? Gender.F : Gender.M;

            java.util.List<com.uravugal.matrimony.dtos.FilteredUserPlanView> results =
                    userRepository.findInterestMatches(
                            oppositeGender.name(), viewer.getCasteId(), userId,
                            h[0], h[1], h[2], h[3], h[4], h[5], h[6],
                            h[7], h[8], h[9], h[10], h[11], h[12], h[13], h[14]);

            resp.setCode(200);
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setMessage("Interest matches fetched");
            resp.setData(results);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setStatus(ResponseStatus.FAILURE);
            resp.setMessage("Error: " + e.getMessage());
        }
        return resp;
    }

    public ResultResponse getProfileDetailByMemberId(String memberId, Gender gender, Integer casteId) {
        ResultResponse response = new ResultResponse();
        try {
            Optional<UserEntity> user = userRepository.findByMemberIdAndGenderAndCasteId(memberId, gender, casteId);
            if (user.isPresent()) {
                response.setCode(200);
                response.setStatus(ResponseStatus.SUCCESS);
                response.setMessage("User profile retrieved successfully");
                response.setData(user.get());
            } else {
                response.setCode(404);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("User not found");
            }
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error retrieving user profile: " + e.getMessage());
        }
        return response;
    }

    public ResultResponse createStarterProfile(HashMap<String, Object> request) {
        ResultResponse response = new ResultResponse();
        try {
            String mobile = request.get("mobileNumber") != null ? request.get("mobileNumber").toString() : null;

            // Ban blacklist check
            if (mobile != null && bannedIdentifierRepository.findByMobile(mobile) != null) {
                response.setCode(403);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("This mobile number cannot be used to create a new account. Please contact support if you believe this is a mistake.");
                return response;
            }

            UserEntity user = new UserEntity();
            user.setFirstName(request.get("fullName").toString());
            user.setMobile(mobile);
            user.setProfileCreated(request.get("profileFor").toString());

            userRepository.save(user);
            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Starter profile created successfully");
            response.setData(user);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error creating starter profile: " + e.getMessage());
        }
        return response;
    }

    
    public ResultResponse getProfileDetailWithIntractionStatus(
        String viewerId,
        Long profileUserId
) {
    ResultResponse response = new ResultResponse();

    try {
        Long viewerUserId = Long.parseLong(
                new String(Base64.getDecoder().decode(viewerId))
        );

        UserEntity profileUser = userRepository.findById(profileUserId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // ---------- BLOCK GATE ----------
        // If either side has blocked the other, the profile is inaccessible.
        if (!viewerUserId.equals(profileUserId)) {
            com.uravugal.matrimony.models.BlockedUser block = blockedUserRepository
                    .findByUsersEitherDirection(viewerUserId, profileUserId);
            if (block != null) {
                response.setCode(403);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("USER_BLOCKED");
                return response;
            }
        }

        // ---------- PROFILE_VIEW_LIMIT GATE ----------
        // Only blocks when a plan's planFeatures row has limit_value > 0. Free and Starter are
        // both seeded at 0 (= unlimited under this guard), and Classic/Silver/Gold/Platinum have
        // no row at all (also unlimited) — every plan currently allows unlimited profile viewing,
        // by design: this vertical typically lets free users browse freely and paywalls the
        // valuable actions (contact reveal, chat, horoscope) instead. Confirmed 2026-07-09.
        if (!viewerUserId.equals(profileUserId)) {
            try {
                UserSubscriptions vSub = userSubscriptionRepository
                        .findTopByUserIdOrderByCreatedAtDesc(viewerUserId);
                Long vPlan = (vSub != null) ? vSub.getSubscriptionPlanId() : null;
                if (vPlan != null && vPlan != 4L && vPlan != 5L && vPlan != 6L) {
                    Features pvFeature = featuresRepository.findByCode("PROFILE_VIEW_LIMIT");
                    if (pvFeature != null) {
                        PlanFeatures pvPlan = planFeaturesRepository.findByFeatureIdAndSubscriptionPlanId(
                                pvFeature.getId(), vPlan);
                        if (pvPlan != null) {
                            int limit = 0;
                            try { limit = Integer.parseInt(pvPlan.getLimitValue()); } catch (Exception ignored) {}
                            if (limit > 0) {
                            com.uravugal.matrimony.models.UserFeatureUsage usage =
                                    userFeatureUsageRepository.findByUserIdAndSubscriptionIdAndFeatureId(
                                            viewerUserId, vSub.getId(), pvFeature.getId())
                                            .orElseGet(() -> {
                                                com.uravugal.matrimony.models.UserFeatureUsage u =
                                                        new com.uravugal.matrimony.models.UserFeatureUsage();
                                                u.setUserId(viewerUserId);
                                                u.setSubscriptionId(vSub.getId());
                                                u.setFeatureId(pvFeature.getId());
                                                u.setUsedCount(0);
                                                return u;
                                            });
                            if (usage.getUsedCount() >= limit) {
                                response.setCode(403);
                                response.setStatus(ResponseStatus.FAILURE);
                                response.setMessage("PROFILE_VIEW_LIMIT_EXCEEDED");
                                return response;
                            }
                            // Only increment if this is a FIRST-TIME view of this profile
                            boolean alreadyViewed = viewedProfileRepository.existsByUserIdValueAndViewedBy(profileUserId, viewerUserId);
                            if (!alreadyViewed) {
                                usage.setUsedCount(usage.getUsedCount() + 1);
                                userFeatureUsageRepository.save(usage);
                            }
                            } // end if (limit > 0)
                        }
                    }
                }
            } catch (Exception ignored) { /* fail-open on lookup errors */ }
        }

        boolean isLiked = userLikeRepository
                .existsByLikerAndLikedUser(viewerUserId, profileUserId);

        boolean isShortlisted = shortlistedProfileRepository
                .existsByShortlistedByAndShortlistedUserIdAndIsActive(
                        viewerUserId,
                        profileUserId,
                        ActiveStatus.Y
                );

        // ---------- INTEREST STATUS ----------
        Map<String, Object> interestMap = new HashMap<>();
        interestMap.put("exists", false);

        Optional<InterestRequest> interestOpt =
                interestRequestRepository.findBetweenUsers(
                        viewerUserId,
                        profileUserId
                );

        if (interestOpt.isPresent()) {
            InterestRequest interest = interestOpt.get();

            interestMap.put("exists", true);
            interestMap.put("status", interest.getAcceptStatus());
            interestMap.put(
                    "sentBy",
                    interest.getInterestSend().equals(viewerUserId)
                            ? "VIEWER"
                            : "PROFILE_USER"
            );
        }

        // ---------- PLAN-BASED FIELD MASKING ----------
        boolean isSelf = viewerUserId.equals(profileUserId);
        Long viewerPlanId = null;
        boolean hasContactAccess = isSelf;
        boolean hasHoroscopeAccess = isSelf;
        boolean isSecureConnect = false;

        if (!isSelf) {
            UserSubscriptions viewerSub = userSubscriptionRepository
                    .findTopByUserIdOrderByCreatedAtDesc(viewerUserId);
            if (viewerSub != null) {
                viewerPlanId = viewerSub.getSubscriptionPlanId();
            }
            if (viewerPlanId != null && viewerPlanId != 1L) {
                // VIEW_PERSONAL_INFO → mobile + email
                Features personalInfo = featuresRepository.findByCode("VIEW_PERSONAL_INFO");
                if (personalInfo != null) {
                    PlanFeatures personalInfoPf = planFeaturesRepository.findByFeatureIdAndSubscriptionPlanId(
                            personalInfo.getId(), viewerPlanId);
                    if (personalInfoPf != null) {
                        String val = personalInfoPf.getLimitValue();
                        // "enabled" or "unlimited" = instant access (Silver+)
                        // numeric value (e.g. "40") = requires explicit contact reveal (Classic)
                        if ("enabled".equalsIgnoreCase(val) || "unlimited".equalsIgnoreCase(val)) {
                            hasContactAccess = true;
                        } else {
                            // Numeric limit — check if already revealed this profile's contact
                            boolean alreadyRevealed = contactRevealRepository.existsByViewerIdAndRevealedUserId(viewerUserId, profileUserId);
                            hasContactAccess = alreadyRevealed;
                        }
                    }
                }
                // HOROSCOPE_VIEW → jathagam / horoscope details
                Features horoscope = featuresRepository.findByCode("HOROSCOPE_VIEW");
                if (horoscope != null) {
                    hasHoroscopeAccess = planFeaturesRepository.findByFeatureIdAndSubscriptionPlanId(
                            horoscope.getId(), viewerPlanId) != null;
                }
                // SECURE_CONNECT → Silver+ sees a masked phone string instead of raw number
                Features secureConnect = featuresRepository.findByCode("SECURE_CONNECT");
                if (secureConnect != null) {
                    isSecureConnect = planFeaturesRepository.findByFeatureIdAndSubscriptionPlanId(
                            secureConnect.getId(), viewerPlanId) != null;
                }
            }
        }

        if (!hasContactAccess) {
            profileUser.setMobile(null);
            profileUser.setEmail(null);
        } else if (hasContactAccess && profileUser.getMobile() != null && !isSelf) {
            // Check if interest is accepted between viewer and profile user
            boolean interestAccepted = false;
            if (interestOpt.isPresent()) {
                interestAccepted = interestOpt.get().getAcceptStatus() == com.uravugal.matrimony.enums.ApprovalStatus.APPROVED;
            }

            if (interestAccepted) {
                // Interest accepted — show full mobile number
                // (keep as-is, no masking)
            } else {
                // Interest NOT accepted — mask first 6 digits, show last 4
                String m = profileUser.getMobile();
                if (m.length() >= 4) {
                    profileUser.setMobile("•••••• " + m.substring(m.length() - 4));
                }
                profileUser.setEmail(null);
            }
        }

        // Mask horoscope — even if plan has access, require interest accepted
        boolean interestAcceptedForHoroscope = false;
        if (interestOpt.isPresent()) {
            interestAcceptedForHoroscope = interestOpt.get().getAcceptStatus() == com.uravugal.matrimony.enums.ApprovalStatus.APPROVED;
        }
        if (!hasHoroscopeAccess || (!isSelf && !interestAcceptedForHoroscope)) {
            try {
                com.uravugal.matrimony.models.UserDetailEntity ud =
                        userDetailRepository.findByUserId(profileUserId);
                if (ud != null) {
                    ud.setHoroscope(null);
                    // Birth time follows the horoscope's rule exactly — it is the same astro
                    // data and is revealed by the same tap (see revealContact).
                    stripBirthTime(ud);
                }
            } catch (Exception ignored) {}
        }

        resolveSubcasteName(profileUser);

        Map<String, Object> data = Map.of(
                "profile", profileUser,
                "interactionStatus", Map.of(
                        "liked", isLiked,
                        "shortlisted", isShortlisted,
                        "interest", interestMap
                )
        );

        response.setCode(200);
        response.setStatus(ResponseStatus.SUCCESS);
        response.setMessage("Profile detail fetched");
        response.setData(data);

    } catch (Exception e) {
        response.setCode(500);
        response.setStatus(ResponseStatus.FAILURE);
        response.setMessage(e.getMessage());
    }

    return response;
}



}
