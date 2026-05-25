package com.uravugal.matrimony.services;

import com.uravugal.matrimony.dtos.*;
import com.uravugal.matrimony.enums.ActiveStatus;
import com.uravugal.matrimony.enums.EmploymentType;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.UserDetailEntity;
import com.uravugal.matrimony.models.UserEntity;
import com.uravugal.matrimony.repositories.UserDetailRepository;
import com.uravugal.matrimony.repositories.UserRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uravugal.matrimony.utils.JsonMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.lang3.StringUtils;  // For StringUtils
import java.util.AbstractMap;  // For AbstractMap

@Service
public class UserDetailService {

    @Autowired
    private UserDetailRepository userDetailRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.uravugal.matrimony.repositories.KeyValueRepository keyValueRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    @Transactional
    public ResultResponse updateAstrologyInfo(AstrologyInfoRequest request) {
        ResultResponse resp = new ResultResponse();
        try {
            // Decode userId from Base64
            Long userId = Long.parseLong(new String(Base64.getDecoder().decode(request.getUserId())));
            UserDetailEntity userDetail = userDetailRepository.findByUserId(userId);
            System.out.println("User detail found: " + userDetail);
            if (userDetail == null) {
                resp.setCode(404);
                resp.setMessage("User detail not found");
                resp.setStatus(ResponseStatus.FAILURE);
                return resp;
            }

            // Get existing astroInfo JSON
            String existingAstroInfo = userDetail.getAstronomicInfo();
            System.out.println("Existing astroInfo: " + existingAstroInfo);

            // Create a new JsonMap to ensure we're working with a fresh object
            JsonMap astroInfo = new JsonMap();
            if (existingAstroInfo != null && !existingAstroInfo.isEmpty()) {
                // Parse the JSON array and get the first element
                JsonMap[] astroInfoArray = objectMapper.readValue(existingAstroInfo, JsonMap[].class);
                if (astroInfoArray.length > 0) {
                    astroInfo = astroInfoArray[0];
                }
            }
            
            // Update the fields
            astroInfo.put("star", request.getStar());
            astroInfo.put("moon_sign", request.getMoonSign());
            astroInfo.put("dosham", request.getDosham());
            
            // Convert back to JSON array
            JsonMap[] updatedArray = new JsonMap[]{astroInfo};
            String updatedAstroInfo = objectMapper.writeValueAsString(updatedArray);
            System.out.println("Updated astroInfo JSON: " + updatedAstroInfo);
            
            // Set the updated JSON string
            userDetail.setAstronomicInfo(updatedAstroInfo);
            
            // Save the entity
            userDetailRepository.save(userDetail);
            
            // Verify the save
            UserDetailEntity savedDetail = userDetailRepository.findByUserId(userId);
            System.out.println("Saved astroInfo: " + savedDetail.getAstronomicInfo());
            
            resp.setCode(200);
            resp.setMessage("Astrology information updated successfully");
            resp.setStatus(ResponseStatus.SUCCESS);
            return resp;

        } catch (Exception e) {
            System.err.println("Error in updateAstrologyInfo: " + e.getMessage());
            e.printStackTrace();
            resp.setCode(500);
            resp.setMessage("Error updating astrology information: " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
            return resp;
        }
    }

@Transactional
public ResultResponse updatePersonalInfo(PersonalInfoRequest request) {
    ResultResponse resp = new ResultResponse();
    
    try {
        System.out.println("Starting updatePersonalInfo");
        System.out.println("Request received: " + request);
        
        Long userId = Long.parseLong(new String(Base64.getDecoder().decode(request.getUserId())));
        System.out.println("Decoded userId: " + userId);

        // Fetch user detail
        UserDetailEntity userDetail = userDetailRepository.findByUserId(userId);
        if (userDetail == null) {
            System.err.println("User detail not found for userId: " + userId);
            resp.setCode(404);
            resp.setMessage("User detail not found");
            resp.setStatus(ResponseStatus.FAILURE);
            return resp;
        }
        System.out.println("User detail found: " + userDetail);

        // Fetch user
        UserEntity user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            System.err.println("User not found for userId: " + userId);
            resp.setCode(404);
            resp.setMessage("User not found");
            resp.setStatus(ResponseStatus.FAILURE);
            return resp;
        }
        System.out.println("User found: " + user);

        // Update user information
        System.out.println("Updating user information");
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        // user.setDob(LocalDate.parse(request.getDateOfBirth()));
        
        // Save user first
        user = userRepository.save(user);
        System.out.println("User saved successfully: " + user);

        // Update basic info
        System.out.println("Updating basic info");
        JsonMap basicInfo = objectMapper.readValue(userDetail.getBasicInfo(), JsonMap.class);
        System.out.println("Current basic info: " + basicInfo);
        
            basicInfo.put("physical_status", request.getPhysicalStatus());
            basicInfo.put("marital_status", request.getMaritalStatus());
            basicInfo.put("mother_language", request.getMotherLanguage());
            basicInfo.put("place_of_birth", request.getPlaceOfBirth());
        
        // Update user detail
        System.out.println("Updating user detail");
        userDetail.setBasicInfo(objectMapper.writeValueAsString(basicInfo));
        userDetail.setHeight(request.getHeight());
        userDetail.setWeight(request.getWeight());
        
        // Save user detail
        userDetail = userDetailRepository.save(userDetail);
        System.out.println("User detail saved successfully: " + userDetail);

        resp.setCode(200);
        resp.setMessage("Personal information updated successfully");
        resp.setStatus(ResponseStatus.SUCCESS);
        System.out.println("Update completed successfully");
        return resp;

    } catch (Exception e) {
        System.err.println("Error in updatePersonalInfo: " + e.getMessage());
        e.printStackTrace();
        resp.setCode(500);
        resp.setMessage("Error updating personal information: " + e.getMessage());
        resp.setStatus(ResponseStatus.FAILURE);
        return resp;
    }
}


    @Transactional
    public ResultResponse updateEducationInfo(EducationInfoRequest request) {
        ResultResponse resp = new ResultResponse();
        try {
            Long userId = Long.parseLong(new String(Base64.getDecoder().decode(request.getUserId())));

            UserDetailEntity userDetail = userDetailRepository.findByUserId(userId);
            if (userDetail == null) {
                resp.setCode(404);
                resp.setMessage("User detail not found");
                resp.setStatus(ResponseStatus.FAILURE);
                return resp;
            }

            // Update education fields
            userDetail.setDegree(request.getEducation());
            userDetail.setOccupation(request.getOccupation());
            
            // Convert string to EmploymentType
            EmploymentType employmentType = EmploymentType.valueOf(request.getEmployedAt().toUpperCase());
            userDetail.setEmployedAt(employmentType);
            
            // Convert string to integer for annual income
            String annualIncome = request.getAnnualIncome();
            userDetail.setAnnualIncome(annualIncome);

            userDetailRepository.save(userDetail);
            
            resp.setCode(200);
            resp.setMessage("Education information updated successfully");
            resp.setStatus(ResponseStatus.SUCCESS);
            return resp;
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Error updating education information: " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
            return resp;
        }
    }

    @Transactional
    public ResultResponse updateFamilyInfo(FamilyInfoRequest request) {
        ResultResponse resp = new ResultResponse();
        try {
            System.out.println("Starting updateFamilyInfo");
            System.out.println("Request received: " + request);
            Long userId = Long.parseLong(new String(Base64.getDecoder().decode(request.getUserId())));

            UserDetailEntity userDetail = userDetailRepository.findByUserId(userId);
            if (userDetail == null) {
                resp.setCode(404);
                resp.setMessage("User detail not found");
                resp.setStatus(ResponseStatus.FAILURE);
                return resp;
            }

            // Get existing familyInfo JSON
            String existingFamilyInfo = userDetail.getFamilyInfo();
            System.out.println("Existing familyInfo: " + existingFamilyInfo);

            // Create a new JsonMap to ensure we're working with a fresh object
            JsonMap familyInfo = new JsonMap();
            if (existingFamilyInfo != null && !existingFamilyInfo.isEmpty()) {
                try {
                    // Try to parse as array first
                    JsonMap[] familyInfoArray = objectMapper.readValue(existingFamilyInfo, JsonMap[].class);
                    if (familyInfoArray.length > 0) {
                        familyInfo = familyInfoArray[0];
                    }
                } catch (Exception e) {
                    // If array parsing fails, try to parse as single object
                    try {
                        familyInfo = objectMapper.readValue(existingFamilyInfo, JsonMap.class);
                    } catch (Exception e2) {
                        // If both fail, just use empty JsonMap
                        familyInfo = new JsonMap();
                    }
                }
            }

            // Update the fields to match creation format
            familyInfo.put("father", nullToEmpty(request.getFatherName()));
            familyInfo.put("mother", nullToEmpty(request.getMotherName()));
            familyInfo.put("father_occupation", nullToEmpty(request.getFatherOccupation()));
            familyInfo.put("mother_occupation", nullToEmpty(request.getMotherOccupation()));
            familyInfo.put("family_status", nullToEmpty(request.getFamilyStatus()));
            familyInfo.put("family_type", nullToEmpty(request.getHouse()));
            familyInfo.put("no_of_siblings", nullToEmpty(request.getNoOfSiblings()));
            familyInfo.put("no_of_brother", nullToEmpty(request.getNoOfBrothers()));
            familyInfo.put("no_of_sister", nullToEmpty(request.getNoOfSisters()));
            familyInfo.put("sister_married", nullToEmpty(request.getNoOfSistersMarried()));
            familyInfo.put("brother_married", nullToEmpty(request.getNoOfBrothersMarried()));
            
            // Convert back to JSON array
            JsonMap[] updatedArray = new JsonMap[]{familyInfo};
            String updatedFamilyInfo = objectMapper.writeValueAsString(updatedArray);
            System.out.println("Updated familyInfo JSON: " + updatedFamilyInfo);
            
            // Set the updated JSON string
            // Ensure the JSON string is properly encoded
            if (updatedFamilyInfo != null) {
                try {
                    // Convert to UTF-8
                    byte[] bytes = updatedFamilyInfo.getBytes("UTF-8");
                    updatedFamilyInfo = new String(bytes, "UTF-8");
                } catch (Exception e) {
                    System.out.println("Error encoding JSON string: " + e.getMessage());
                }
            }
            
            userDetail.setFamilyInfo(updatedFamilyInfo);
            // userDetail.se
            // Save the entity
            userDetailRepository.save(userDetail);
            
            
            // Verify the save
            UserDetailEntity savedDetail = userDetailRepository.findByUserId(userId);
            System.out.println("Saved familyInfo: " + savedDetail.getFamilyInfo());
            
            resp.setCode(200);
            resp.setMessage("Family information updated successfully");
            resp.setStatus(ResponseStatus.SUCCESS);
            return resp;

        } catch (Exception e) {
            System.err.println("Error in updateFamilyInfo: " + e.getMessage());
            e.printStackTrace();
            resp.setCode(500);
            resp.setMessage("Error updating family information: " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
            return resp;
        }
    }
            
            // Convert back to JSON string
        //     String updatedFamilyInfo = objectMapper.writeValueAsString(familyInfo);
        //     userDetail.setFamilyInfo(updatedFamilyInfo);

        //     userDetailRepository.save(userDetail);
            
        //     resp.setCode(200);
        //     resp.setMessage("Family information updated successfully");
        //     resp.setStatus(ResponseStatus.SUCCESS);
        //     return resp;
        // } catch (Exception e) {
        //     resp.setCode(500);
        //     resp.setMessage("Error updating family information: " + e.getMessage());
        //     resp.setStatus(ResponseStatus.FAILURE);
        //     return resp;
        // }
    // }

// ── Interest / Hobbies methods ──

    /** Lazily loaded from keyValues table (key=interestTags). Falls back to hardcoded if not seeded. */
    private java.util.Set<String> getAllowedHobbyCodes() {
        try {
            java.util.Optional<com.uravugal.matrimony.models.KeyValue> kv =
                    keyValueRepository.findByKeyColumn("interestTags");
            if (kv.isPresent() && kv.get().getValueColumn() != null) {
                java.util.List<java.util.Map<String, String>> tags =
                        new com.fasterxml.jackson.databind.ObjectMapper()
                                .readValue(kv.get().getValueColumn(),
                                        new com.fasterxml.jackson.core.type.TypeReference<java.util.List<java.util.Map<String, String>>>() {});
                return tags.stream().map(t -> t.get("code")).filter(c -> c != null)
                        .collect(java.util.stream.Collectors.toSet());
            }
        } catch (Exception ignored) {}
        // Hardcoded fallback
        return java.util.Set.of(
                "food", "travel", "photography", "music", "reading", "cricket",
                "yoga", "movies", "technology", "fitness", "art", "dance",
                "cooking", "gardening", "spirituality"
        );
    }

    private java.util.List<String> parseHobbiesJson(String json) {
        if (json == null || json.isBlank()) return new java.util.ArrayList<>();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(json, new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {});
        } catch (Exception e) { return new java.util.ArrayList<>(); }
    }

    public ResultResponse getHobbies(String encodedUserId) {
        ResultResponse resp = new ResultResponse();
        try {
            Long userId = Long.parseLong(new String(java.util.Base64.getDecoder().decode(encodedUserId)));
            UserDetailEntity ud = userDetailRepository.findByUserId(userId);
            java.util.List<String> hobbies = ud != null ? parseHobbiesJson(ud.getHobbies()) : java.util.Collections.emptyList();
            resp.setCode(200);
            resp.setStatus(com.uravugal.matrimony.enums.ResponseStatus.SUCCESS);
            resp.setMessage("Hobbies fetched");
            resp.setData(java.util.Map.of("hobbies", hobbies));
        } catch (Exception e) {
            resp.setCode(500);
            resp.setStatus(com.uravugal.matrimony.enums.ResponseStatus.FAILURE);
            resp.setMessage("Error: " + e.getMessage());
        }
        return resp;
    }

    public ResultResponse updateHobbies(String encodedUserId, java.util.List<String> hobbies) {
        ResultResponse resp = new ResultResponse();
        try {
            Long userId = Long.parseLong(new String(java.util.Base64.getDecoder().decode(encodedUserId)));
            UserDetailEntity ud = userDetailRepository.findByUserId(userId);
            if (ud == null) {
                resp.setCode(404);
                resp.setStatus(com.uravugal.matrimony.enums.ResponseStatus.FAILURE);
                resp.setMessage("User details not found");
                return resp;
            }
            // Validate codes against DB-driven allowed list
            java.util.Set<String> allowedCodes = getAllowedHobbyCodes();
            java.util.List<String> valid = hobbies != null
                    ? hobbies.stream().filter(allowedCodes::contains).distinct().collect(java.util.stream.Collectors.toList())
                    : java.util.Collections.emptyList();
            ud.setHobbies(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(valid));
            userDetailRepository.save(ud);
            resp.setCode(200);
            resp.setStatus(com.uravugal.matrimony.enums.ResponseStatus.SUCCESS);
            resp.setMessage("Hobbies updated");
            resp.setData(java.util.Map.of("hobbies", valid));
        } catch (Exception e) {
            resp.setCode(500);
            resp.setStatus(com.uravugal.matrimony.enums.ResponseStatus.FAILURE);
            resp.setMessage("Error: " + e.getMessage());
        }
        return resp;
    }

    public ResultResponse getCommonInterests(String viewerEncodedId, Long profileUserId) {
        ResultResponse resp = new ResultResponse();
        try {
            Long viewerId = Long.parseLong(new String(java.util.Base64.getDecoder().decode(viewerEncodedId)));
            UserDetailEntity viewerUd = userDetailRepository.findByUserId(viewerId);
            UserDetailEntity profileUd = userDetailRepository.findByUserId(profileUserId);
            java.util.List<String> viewerHobbies = parseHobbiesJson(viewerUd != null ? viewerUd.getHobbies() : null);
            java.util.List<String> profileHobbies = parseHobbiesJson(profileUd != null ? profileUd.getHobbies() : null);
            java.util.List<String> common = new java.util.ArrayList<>(viewerHobbies);
            common.retainAll(profileHobbies);
            resp.setCode(200);
            resp.setStatus(com.uravugal.matrimony.enums.ResponseStatus.SUCCESS);
            resp.setMessage("Common interests fetched");
            resp.setData(java.util.Map.of("common", common, "count", common.size(),
                    "viewerHobbies", viewerHobbies, "profileHobbies", profileHobbies));
        } catch (Exception e) {
            resp.setCode(500);
            resp.setStatus(com.uravugal.matrimony.enums.ResponseStatus.FAILURE);
            resp.setMessage("Error: " + e.getMessage());
        }
        return resp;
    }

    public ResultResponse getInterestBasedMatches(String encodedUserId, int limit) {
        ResultResponse resp = new ResultResponse();
        try {
            Long userId = Long.parseLong(new String(java.util.Base64.getDecoder().decode(encodedUserId)));
            com.uravugal.matrimony.models.UserEntity viewer = userRepository.findById(userId).orElse(null);
            if (viewer == null || viewer.getCasteId() == null || viewer.getGender() == null) {
                resp.setCode(404);
                resp.setStatus(com.uravugal.matrimony.enums.ResponseStatus.FAILURE);
                resp.setMessage("User not found or incomplete profile");
                return resp;
            }
            UserDetailEntity viewerUd = userDetailRepository.findByUserId(userId);
            java.util.List<String> viewerHobbies = parseHobbiesJson(viewerUd != null ? viewerUd.getHobbies() : null);
            if (viewerHobbies.isEmpty()) {
                resp.setCode(200);
                resp.setStatus(com.uravugal.matrimony.enums.ResponseStatus.SUCCESS);
                resp.setMessage("No hobbies set — add interests first");
                resp.setData(java.util.Collections.emptyList());
                return resp;
            }
            com.uravugal.matrimony.enums.Gender oppositeGender =
                    viewer.getGender() == com.uravugal.matrimony.enums.Gender.M
                            ? com.uravugal.matrimony.enums.Gender.F
                            : com.uravugal.matrimony.enums.Gender.M;
            java.util.List<com.uravugal.matrimony.models.UserEntity> candidates =
                    userRepository.findAllByCasteIdAndGenderAndIsActiveAndIsUserNot(
                            viewer.getCasteId(), oppositeGender, com.uravugal.matrimony.enums.ActiveStatus.Y, com.uravugal.matrimony.enums.IsUser.ADM);

            // Score each candidate by shared-hobby count
            java.util.List<java.util.Map<String, Object>> scored = new java.util.ArrayList<>();
            for (com.uravugal.matrimony.models.UserEntity c : candidates) {
                if (c.getUserId().equals(userId)) continue;
                UserDetailEntity cUd = userDetailRepository.findByUserId(c.getUserId());
                java.util.List<String> cHobbies = parseHobbiesJson(cUd != null ? cUd.getHobbies() : null);
                java.util.List<String> common = new java.util.ArrayList<>(viewerHobbies);
                common.retainAll(cHobbies);
                if (common.isEmpty()) continue;
                java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("userId", c.getUserId());
                row.put("firstName", c.getFirstName());
                row.put("lastName", c.getLastName());
                row.put("age", c.getAge());
                row.put("location", c.getLocation());
                row.put("profileImage", c.getProfileImage());
                row.put("gender", c.getGender() != null ? c.getGender().name() : null);
                row.put("sharedCount", common.size());
                row.put("sharedInterests", common);
                row.put("idVerified", Boolean.TRUE.equals(c.getIdVerified()));
                row.put("educationVerified", Boolean.TRUE.equals(c.getEducationVerified()));
                row.put("incomeVerified", Boolean.TRUE.equals(c.getIncomeVerified()));
                scored.add(row);
            }
            scored.sort((a, b) -> ((Integer) b.get("sharedCount")).compareTo((Integer) a.get("sharedCount")));
            java.util.List<java.util.Map<String, Object>> topMatches = scored.subList(0, Math.min(scored.size(), limit));
            resp.setCode(200);
            resp.setStatus(com.uravugal.matrimony.enums.ResponseStatus.SUCCESS);
            resp.setMessage("Interest-based matches fetched");
            resp.setData(topMatches);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setStatus(com.uravugal.matrimony.enums.ResponseStatus.FAILURE);
            resp.setMessage("Error: " + e.getMessage());
        }
        return resp;
    }

public ResultResponse calculateProfileCompletion(String userId) {
    ResultResponse response = new ResultResponse();
    try {
        // Decode and validate user
        Decoder decoder = Base64.getDecoder();
        Long userIdLong = Long.parseLong(new String(decoder.decode(userId)));
        UserEntity userEntity = userRepository.findById(userIdLong)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Define fields to check
// Define fields to check
List<String> requiredFields = new ArrayList<>(Arrays.asList(
    "horoscope", "profileImage", "height", "weight", "star",
    "moonSign", "dosham", "maritalStatus", "motherLanguage",
    "familyType", "familyStatus", "numberOfSiblings",
    "noOfBrothers", "noOfSisters", "brotherMarried", "sisterMarried",
    "hobbies"
));

int totalFields = requiredFields.size();
        AtomicInteger completedFields = new AtomicInteger(0);
        List<String> missingFields = new ArrayList<>(requiredFields);
        Map<String, Object> nextActions = new LinkedHashMap<>(); // To track potential next actions


        if (userEntity.getProfileImage() != null && !userEntity.getProfileImage().isEmpty()) {
            completedFields.incrementAndGet();
            missingFields.remove("profileImage");
        } else {
            nextActions.put("PROFILE_IMAGE", createAction("Add Profile Photo", "Get 5x more profile views", 20, "/(root)/(tabs)/profile"));
        }

        // Fetch UserDetailEntity
        UserDetailEntity userDetails = userDetailRepository.findByUserId(userIdLong);
        if (userDetails == null) {
            throw new RuntimeException("User details not found");
        }

        // Check UserDetailEntity fields
        if (userDetails.getHoroscope() != null && !userDetails.getHoroscope().isEmpty()) {
            completedFields.incrementAndGet();
            missingFields.remove("horoscope");
        } else {
            nextActions.put("HOROSCOPE", createAction("Add Horoscope", "Improves match accuracy", 15, "/(root)/(tabs)/profile"));
        }

        System.out.println("userDetails.getFamilyInfo()"+userDetails.getFamilyInfo());
        // Parse JSON fields with null checks
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode astroInfo = parseJsonSafely(objectMapper, userDetails.getAstronomicInfo());
        JsonNode basicInfo = parseJsonSafely(objectMapper, userDetails.getBasicInfo());
        JsonNode familyInfo = parseJsonSafely(objectMapper, userDetails.getFamilyInfo());
        System.out.println("familyInfo1"+familyInfo);

        // Check basic info fields
        if (basicInfo != null) {
            checkField(basicInfo, "marital_status", "maritalStatus", missingFields, completedFields::incrementAndGet);
            checkField(basicInfo, "mother_language", "motherLanguage", missingFields, completedFields::incrementAndGet);
            
            String h = userDetails.getHeight() != null ? userDetails.getHeight().trim() : "";
            boolean hasHeight = !h.isEmpty() && !h.equals("-") && !h.equalsIgnoreCase("Not specified");
            if (hasHeight) {
                completedFields.incrementAndGet();
                missingFields.remove("height");
            }

            String w = userDetails.getWeight() != null ? userDetails.getWeight().trim() : "";
            boolean hasWeight = !w.isEmpty() && !w.equals("-") && !w.equalsIgnoreCase("Not specified");
            if (hasWeight) {
                completedFields.incrementAndGet();
                missingFields.remove("weight");
            }

            // Show the right next action based on what's missing
            if (!hasHeight && !hasWeight) {
                nextActions.put("HEIGHT_WEIGHT", createAction("Add Height & Weight", "Complete your physical details", 10, "/(root)/(tabs)/profile"));
            } else if (!hasHeight) {
                nextActions.put("HEIGHT", createAction("Add Height", "Help others find you", 10, "/(root)/(tabs)/profile"));
            } else if (!hasWeight) {
                nextActions.put("WEIGHT", createAction("Add Weight", "Complete your physical details", 10, "/(root)/(tabs)/profile"));
            }
        }

        // Check astro info fields
        if (astroInfo != null && astroInfo.size() > 0) {
            JsonNode firstAstro = astroInfo.get(0);
            checkField(firstAstro, "star", "star", missingFields, completedFields::incrementAndGet);
            checkField(firstAstro, "moon_sign", "moonSign", missingFields, completedFields::incrementAndGet);
            checkField(firstAstro, "dosham", "dosham", missingFields, completedFields::incrementAndGet);
        }

        // Check family info fields
// Check family info fields
if (familyInfo != null && familyInfo.isArray() && familyInfo.size() > 0) {
    JsonNode firstFamily = familyInfo.get(0);  // Get the first element
    System.out.println("firstFamily: " + firstFamily);
    
    // Use the exact field names from the JSON
    checkField(firstFamily, "family_type", "familyType", missingFields, completedFields::incrementAndGet);
    checkField(firstFamily, "family_status", "familyStatus", missingFields, completedFields::incrementAndGet);
    checkField(firstFamily, "no_of_siblings", "numberOfSiblings", missingFields, completedFields::incrementAndGet);
    checkField(firstFamily, "no_of_brother", "noOfBrothers", missingFields, completedFields::incrementAndGet);
    checkField(firstFamily, "no_of_sister", "noOfSisters", missingFields, completedFields::incrementAndGet);
    checkField(firstFamily, "brother_married", "brotherMarried", missingFields, completedFields::incrementAndGet);
    checkField(firstFamily, "sister_married", "sisterMarried", missingFields, completedFields::incrementAndGet);
}

        // Check hobbies/interests
        if (userDetails.getHobbies() != null && !userDetails.getHobbies().isEmpty()
                && !userDetails.getHobbies().equals("[]") && !userDetails.getHobbies().equals("null")) {
            completedFields.incrementAndGet();
            missingFields.remove("hobbies");
        } else {
            nextActions.put("HOBBIES", createAction("Add Your Interests",
                    "Get matched with people who share your passions", 10, "/(root)/(tabs)/profile"));
        }

        // Calculate completion percentage
int percentage = (int) Math.round((completedFields.get() * 100.0) / totalFields);        
        // Set completion status
        String status = percentage >= 80 ? "STRONG" : 
                       percentage >= 50 ? "GOOD" : "BASIC";
        String label = percentage >= 80 ? "Strong Profile" : 
                      percentage >= 50 ? "Good Profile" : "Basic Profile";

        // Build response
        Map<String, Object> responseData = new HashMap<>();
        
        // Completion info
        responseData.put("completion", Map.of(
            "percentage", percentage,
            "status", status,
            "label", label
        ));
        
        // Next action - always show the next action with highest priority
        Map.Entry<String, Object> nextAction = !nextActions.isEmpty() ? 
            nextActions.entrySet().iterator().next() : 
            createDefaultNextAction(missingFields);
        responseData.put("nextAction", nextAction != null ? nextAction.getValue() : null);
        
        // Meta info
        responseData.put("meta", Map.of(
            "totalFields", totalFields,
            "completedFields", completedFields.get(),
            "missingFields", missingFields
        ));

        response.setCode(200);
        response.setStatus(ResponseStatus.SUCCESS);
        response.setMessage("Profile completion calculated successfully");
        response.setData(responseData);
        
        return response;

    } catch (Exception e) {
        response.setCode(500);
        response.setStatus(ResponseStatus.FAILURE);
        response.setMessage("Error calculating profile completion: " + e.getMessage());
        return response;
    }
}

private Map.Entry<String, Object> createDefaultNextAction(List<String> missingFields) {
    if (missingFields.isEmpty()) {
        return null;
    }
    String firstMissing = missingFields.get(0);
    // Convert camelCase to Title Case with spaces and add "Add" prefix
    String title = "Add " + StringUtils.capitalize(firstMissing.replaceAll("([A-Z])", " $1").toLowerCase());
    return new AbstractMap.SimpleEntry<>(
        firstMissing.toUpperCase(),
        // createAction(title, "Complete your profile information", 5, "/profile/edit?field=" + firstMissing)
        createAction(title, "Complete your profile information", 5, "/(root)/(tabs)/profile")

        
    );
}

// Helper method to create action objects
private Map<String, Object> createAction(String title, String description, int boost, String route) {
    Map<String, Object> action = new HashMap<>();
    action.put("title", title);
    action.put("description", description);
    action.put("boostPercentage", boost);
    action.put("route", route);
    return action;
}

// Helper method to safely parse JSON
private JsonNode parseJsonSafely(ObjectMapper mapper, String json) {
    if (json == null || json.trim().isEmpty()) {
        return null;
    }
    try {
        return mapper.readTree(json);
    } catch (Exception e) {
        return null;
    }
}

// Helper method to check and update field completion
private void checkField(JsonNode node, String fieldName, String fieldKey,
                       List<String> missingFields, Runnable onComplete) {
    if (node == null) return;
    JsonNode fieldNode = node.get(fieldName);
    if (fieldNode != null && !fieldNode.isNull()) {
        String val = fieldNode.asText().trim();
        // Exclude placeholder/empty values
        if (!val.isEmpty() && !val.equals("-") && !val.equalsIgnoreCase("Not specified")
                && !val.equalsIgnoreCase("null") && !val.equalsIgnoreCase("NA")
                && !val.equalsIgnoreCase("N/A")) {
            onComplete.run();
            missingFields.remove(fieldKey);
        }
    }
}



    @Transactional
    public ResultResponse updateAbout(Long userId, String about) {
        ResultResponse response = new ResultResponse();
        try {
            UserDetailEntity userDetail = userDetailRepository.findByUserId(userId);
            
            if (userDetail == null) {
                throw new RuntimeException("User detail not found for userId: " + userId);
            }

            userDetail.setAbout(about);
            userDetailRepository.save(userDetail);
            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("About information updated successfully");
            return response;
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error updating about information: " + e.getMessage());
            return response;
        }
    }
}