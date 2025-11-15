package com.uravugal.matrimony.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.dtos.UserFilterRequest;
import com.uravugal.matrimony.dtos.UserDetailUpdateRequest;
import com.uravugal.matrimony.dtos.UserProfileRequest;
import com.uravugal.matrimony.enums.ActiveStatus;
import com.uravugal.matrimony.enums.ApprovalStatus;
import com.uravugal.matrimony.enums.EmploymentType;
import com.uravugal.matrimony.enums.Gender;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.UserDetailEntity;
import com.uravugal.matrimony.models.UserEntity;
import com.uravugal.matrimony.models.UserSubscriptions;
import com.uravugal.matrimony.repositories.UserRepository;
import com.uravugal.matrimony.repositories.UserSubscriptionsRepository;
import com.uravugal.matrimony.repositories.UserDetailRepository;
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


@Service
public class UserService {

    private static final String PREFIX = "UMR";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserDetailRepository userDetailRepository;
    
    @Autowired
    private S3FileUploadService s3UploadService;

    @Autowired
    private UserSubscriptionsRepository userSubscriptionRepository;
    
    private static final String AWS_BASE_PATH = "profile-images/";


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
                    //     String encodedPin = Base64.getEncoder().encodeToString(userEntity.getPin().getBytes());
                    //     existingUser.setPin(encodedPin);
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
                    //  if (userEntity.getPin() != null) {
                    //     userEntity.setPin(EncryptionUtils.encryptPin(userEntity.getPin()));
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
            String decodedId = new String(Base64.getDecoder().decode(encodedId));
            Long id = Long.parseLong(decodedId);
            
            Optional<UserEntity> user = userRepository.findById(id);
            if (user.isPresent()) {
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

    public ResultResponse getProfileDetailByUserId(Long userId) {
        ResultResponse response = new ResultResponse();
        try {
            Optional<UserEntity> user = userRepository.findById(userId);
            if (user.isPresent()) {
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


    public ResultResponse getUserDetailByCaste(Integer casteId, Gender gender) {
        ResultResponse response = new ResultResponse();
        try {
            gender = gender == Gender.M ? Gender.F : Gender.M;
            List<UserEntity> user = userRepository.findAllByCasteIdAndGenderAndIsActive(casteId, gender, ActiveStatus.Y);
            if (user.size() > 0) {
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

    public ResultResponse getUserDetailByCasteIdAndLocation(Integer casteId, Gender gender, String location) {
        ResultResponse response = new ResultResponse();
        try {
            List<UserEntity> users = userRepository.findAllByCasteIdAndGenderAndLocationAndIsActive(casteId, gender, location,
                    ActiveStatus.Y);
                    
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


    public ResultResponse getDailyShuffledUsersByCaste(Integer casteId, Gender gender) {
        ResultResponse response = new ResultResponse();
        try {
            List<UserEntity> users = userRepository.findAllByCasteIdAndGenderAndIsActive(casteId, gender, ActiveStatus.Y);

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

    public ResultResponse getTop30NewUsers(Integer casteId,Gender gender) {
        ResultResponse response = new ResultResponse();
        try {
            List<UserEntity> users = userRepository.findTop30ByCasteIdAndGenderAndIsActiveOrderByCreatedAtDesc(casteId,gender,ActiveStatus.Y);
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

    public ResultResponse getTenShuffledUsers(Gender gender, Integer casteId) {
        ResultResponse response = new ResultResponse();
        try {
            // Get users filtered by gender and casteId
            List<UserEntity> users = userRepository.findAllByGenderAndCasteIdAndIsActiveOrderByRandom(gender, casteId, ActiveStatus.Y);
            
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
    //     ResultResponse response = new ResultResponse();
    //     try {
    //         // Get filtered users
    //         System.out.println("Filter request: " + filterRequest);
    //         List<UserEntity> users = userRepository.findFilteredUsers(
    //                 ActiveStatus.Y,
    //                 filterRequest.getGender() != null ? Gender.valueOf(filterRequest.getGender()) : null,
    //                 filterRequest.getCasteId(),
    //                 filterRequest.getMinAge(),
    //                 filterRequest.getMaxAge(),
    //                 filterRequest.getMinAnnualIncome(),
    //                 filterRequest.getMaxAnnualIncome(),
    //                 filterRequest.getOccupation(),
    //                 filterRequest.getLocation(),
    //                 filterRequest.getEmployedAt() != null ? EmploymentType.valueOf(filterRequest.getEmployedAt()) : null, 
    //                 filterRequest.getProfileImageStatus() != null ? filterRequest.getProfileImageStatus() : "N"
    //         );
    //         System.out.println("Filtered users: " + users);
            
    //         if (users.isEmpty()) {
    //             response.setCode(404);
    //             response.setStatus(ResponseStatus.FAILURE);
    //             response.setMessage("No users found matching the criteria.");
    //             return response;
    //         }

    //         // Take first 10 users (already shuffled by the query)
    //         List<UserEntity> filteredUsers = users.subList(0, Math.min(users.size(), 10));

    //         response.setCode(200);
    //         response.setStatus(ResponseStatus.SUCCESS);
    //         response.setMessage("Filtered users fetched successfully.");
    //         response.setData(filteredUsers);
    //     } catch (Exception e) {
    //         response.setCode(500);
    //         response.setStatus(ResponseStatus.FAILURE);
    //         response.setMessage("Error fetching filtered users: " + e.getMessage());
    //     }
    //     return response;
    // }

    public ResultResponse filterUsers(UserFilterRequest filterRequest) {
        ResultResponse response = new ResultResponse();
        List<UserEntity> users = new ArrayList<>();
        try {
            if(filterRequest.getUserId() != null){
                UserSubscriptions userSubscriptions = userSubscriptionRepository.findTopByUserIdOrderByCreatedAtDesc(filterRequest.getUserId());
                if(userSubscriptions == null){
                    response.setCode(404);
                    response.setStatus(ResponseStatus.FAILURE);
                    response.setMessage("No subscriptions found for the specified user.");
                    return response;
                }
                if(userSubscriptions.getSubscriptionPlanId() != 1){
                    System.out.println("premium userrr request: ==========>" + filterRequest);
                    users = userRepository.advanceFilter(
                        filterRequest.getGender() != null ? filterRequest.getGender() : null,          // Pass String directly
                        filterRequest.getCasteId(),
                        filterRequest.getMinAge() != null ? Integer.valueOf(filterRequest.getMinAge()) : null,
                        filterRequest.getMaxAge() != null ? Integer.valueOf(filterRequest.getMaxAge()) : null,
                        filterRequest.getMinAnnualIncome() != null ? Integer.valueOf(filterRequest.getMinAnnualIncome()) : null,
                        filterRequest.getMaxAnnualIncome() != null ? Integer.valueOf(filterRequest.getMaxAnnualIncome()) : null,
                        filterRequest.getOccupation(),
                        filterRequest.getLocation(),
                        filterRequest.getEmployedAt() != null ? filterRequest.getEmployedAt() : null,  // Pass String directly
                        filterRequest.getProfileImageStatus() != null ? filterRequest.getProfileImageStatus() : "N",
                        filterRequest.getStar(),
                        filterRequest.getDosham(),
                        filterRequest.getProfilesWithHoroscope() != null ? filterRequest.getProfilesWithHoroscope() : "N"
                );
                
                }else{
                    System.out.println("Normal user request: ==========>" + filterRequest);
                    users = userRepository.normalFilter(
                        ActiveStatus.Y,
                        filterRequest.getGender() != null ? Gender.valueOf(filterRequest.getGender()) : null,
                        filterRequest.getCasteId(),
                        filterRequest.getMinAge() != null ? Integer.valueOf(filterRequest.getMinAge()) : null,
                        filterRequest.getMaxAge() != null ? Integer.valueOf(filterRequest.getMaxAge()) : null,
                        filterRequest.getLocation(),
                        filterRequest.getProfileImageStatus() != null ? filterRequest.getProfileImageStatus() : "N"
                    );
                    
                }
            }else{
                response.setCode(404);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("User Id Is Required");
                return response;
            }
            // Get filtered users
         
            System.out.println("Filtered users:==============================> " + users.size());
            
            if (users.size() > 0) {
                response.setCode(200);
                response.setStatus(ResponseStatus.SUCCESS);
                response.setMessage("Filtered users fetched successfully.");
                response.setData(users);
                return response;
            }else{
                response.setCode(404);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("No users found matching the criteria.");
                return response;
            }   

            // Take first 10 users (already shuffled by the query)
            // List<UserEntity> filteredUsers = users.subList(0, Math.min(users.size(), 10));

         
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching filtered users: " + e.getMessage());
        }
        return response;
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

    @Transactional
    public ResultResponse createUser(UserProfileRequest request) {
        ResultResponse response = new ResultResponse();
        try {
            System.out.println("User request: " + request);
            UserDetailEntity userDetail = new UserDetailEntity();
            UserEntity user = userRepository.findByMobile(request.getMobileNumber());
    
            if (user != null) {
                response.setCode(401);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("User already exists");
                return response;
            }
    
            // Save user entity
            UserEntity userEntity = new UserEntity();
            userEntity.setFirstName(request.getFirstName());
            userEntity.setLastName(request.getLastName());
            userEntity.setMobile(request.getMobileNumber());
            userEntity.setGender(Gender.valueOf(request.getGender()));
            userEntity.setCasteId(request.getCasteId());
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
            userDetail.setEmployedAt(EmploymentType.valueOf(request.getEmployingIn().toUpperCase()));
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
    
            response.setCode(200);
            response.setMessage("User profile created successfully");
            response.setStatus(ResponseStatus.SUCCESS);
            return response;
    
        } catch (Exception e) {
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
            // Find the user            UserEntity user = userRepository.findById(id).orElse(null);
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
                    
                    // Upload to S3
                    String fileUrl = s3UploadService.uploadGalleryImage(tempFile, AWS_BASE_PATH + "user_" + userId);
                    System.out.println("File URL: ------>" + fileUrl);
                    
                    // Extract the key part from the URL if needed
                    int startIndex = fileUrl.indexOf("https://");
                    if (startIndex != -1) {
                        String domain = fileUrl.substring(0, fileUrl.indexOf("/", 8)); // Get domain part
                        fileUrl = domain + "/" + AWS_BASE_PATH + "user_" + userId + "/" + fileName;
                    }
                    
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
                userentity = userRepository.findByMobile(user.getMobile());
                if (userentity == null) {
                    finalresult.setMessage("User Not Found");
                    finalresult.setStatus(ResponseStatus.FAILURE);
                    finalresult.setCode(404);
                    return finalresult;
                }
            } else {
                finalresult.setMessage("Mobile Number Required");
                finalresult.setStatus(ResponseStatus.FAILURE);
                finalresult.setCode(404);
                return finalresult;
            }

            String decodedValue = new String(Base64.getDecoder().decode(userentity.getPin()));

            if (user.getPin() != null && (userentity.getPin() == null || userentity.getPin().equals(""))) {
                finalresult.setMessage("Pin Not Setted.");
                finalresult.setStatus(ResponseStatus.FAILURE);
                finalresult.setCode(204);
                return finalresult;
            }

            if (decodedValue.equals(user.getPin())) {
                String valueBfr = String.valueOf(userentity.getUserId());
                String base64String = Base64.getEncoder().encodeToString(valueBfr.getBytes());

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



                // userRepository.save(userentity);

                finalresult.setData(returnValue);
                finalresult.setMessage("Login Success");
                finalresult.setStatus(ResponseStatus.SUCCESS);
                finalresult.setCode(200);
            } else {
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

    public void updateLastSeen(Long userId, boolean isOnline) {
        Optional<UserEntity> optionalUser = userRepository.findById(userId);
        if (optionalUser.isPresent()) {
            UserEntity user = optionalUser.get();
            user.setOnline(isOnline);
            user.setLastSeen(LocalDateTime.now());
            userRepository.save(user);
        }
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
    
}
