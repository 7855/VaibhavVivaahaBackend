package com.uravugal.matrimony.services;

import com.uravugal.matrimony.dtos.*;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.List;


@Service
public class UserDetailService {

    @Autowired
    private UserDetailRepository userDetailRepository;

    @Autowired
    private UserRepository userRepository;

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

     public ResultResponse calculateProfileCompletion(String userId) {
        ResultResponse response = new ResultResponse();
        try {
            Decoder decoder = Base64.getDecoder();
            Long userIdLong = Long.parseLong(new String(decoder.decode(userId)));
            UserEntity userEntity = userRepository.findById(userIdLong).get();
            if (userEntity == null) {
                throw new RuntimeException("User details not found");
            }

            List<String> requiredFields = Arrays.asList(
                "horoscope",
                "mobile",
                "profileImage",
                "height",
                "weight",
                "physicalStatus",
                "maritalStatus",
                "motherLanguage",
                "star",
                "moonSign",
                "dosham",
                "familyType",
                "familyStatus",
                "numberOfSiblings"
            );

            int totalFields = requiredFields.size();
            int completedFields = 0;
            List<String> missingFields = new ArrayList<>(requiredFields); // Start with all fields as missing

            // Check UserEntity fields
            if (userEntity != null) {
                if (userEntity.getMobile() != null && !userEntity.getMobile().isEmpty()) {
                    completedFields++;
                    missingFields.remove("mobile");
                }
                
                if (userEntity.getProfileImage() != null && !userEntity.getProfileImage().isEmpty()) {
                    completedFields++;
                    missingFields.remove("profileImage");
                }
            }

            // Fetch UserDetailEntity separately
            UserDetailEntity userDetails = userDetailRepository.findByUserId(userIdLong);
            if (userDetails == null) {
                throw new RuntimeException("User details not found");
            }

            // Check UserDetailEntity fields
            if (userDetails.getHoroscope() != null && !userDetails.getHoroscope().isEmpty()) {
                completedFields++;
                missingFields.remove("horoscope");
            }

            // Parse JSON fields
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode astroInfo = null;
            JsonNode basicInfo = null;
            JsonNode familyInfo = null;
            
            try {
                astroInfo = objectMapper.readTree(userDetails.getAstronomicInfo());
            } catch (Exception e) {
                // Handle JSON parsing error
                astroInfo = null;
            }

            try {
                basicInfo = objectMapper.readTree(userDetails.getBasicInfo());
            } catch (Exception e) {
                // Handle JSON parsing error
                basicInfo = null;
            }

            try {
                familyInfo = objectMapper.readTree(userDetails.getFamilyInfo());
            } catch (Exception e) {
                // Handle JSON parsing error
                familyInfo = null;
            }

            // Parse astronomicInfo
            if (astroInfo != null) {
                JsonNode starNode = astroInfo.get(0).get("star");
                JsonNode moonSignNode = astroInfo.get(0).get("moon_sign");
                JsonNode doshamNode = astroInfo.get(0).get("dosham");
                
                if (starNode != null && !starNode.isNull()) {
                    completedFields++;
                    missingFields.remove("star");
                }
                if (moonSignNode != null && !moonSignNode.isNull()) {
                    completedFields++;
                    missingFields.remove("moonSign");
                }
                if (doshamNode != null && !doshamNode.isNull()) {
                    completedFields++;
                    missingFields.remove("dosham");
                }
            }

            // Parse basicInfo
            if (basicInfo != null) {
                // JsonNode heightNode = basicInfo.get("height");
                // JsonNode weightNode = basicInfo.get("weight");

                JsonNode physicalStatusNode = basicInfo.get("physical_status");
                JsonNode maritalStatusNode = basicInfo.get("marital_status");
                JsonNode motherLanguage = basicInfo.get("mother_language");
                
                if (motherLanguage != null && !motherLanguage.isNull()) {
                    completedFields++;
                    missingFields.remove("motherLanguage");
                }

                if (userDetails.getHeight() != null && !userDetails.getHeight().isEmpty()) {
                    completedFields++;
                    missingFields.remove("height");
                }
                if (userDetails.getWeight() != null && !userDetails.getWeight().isEmpty()) {
                    completedFields++;
                    missingFields.remove("weight");
                }
                if (physicalStatusNode != null && !physicalStatusNode.isNull()) {
                    completedFields++;
                    missingFields.remove("physicalStatus");
                }
                if (maritalStatusNode != null && !maritalStatusNode.isNull()) {
                    completedFields++;
                    missingFields.remove("maritalStatus");
                }
            }

            // Parse familyInfo
          
            if (familyInfo != null) {
                JsonNode familyTypeNode = familyInfo.get(0).get("familyType");
                JsonNode familyStatusNode = familyInfo.get(0).get("familyStatus");
                JsonNode numberOfSiblingsNode = familyInfo.get(0).get("numberOfSiblings");
                
           
                if (familyTypeNode != null && !familyTypeNode.isNull()) {
                    completedFields++;
                    missingFields.remove("familyType");
                }
                if (familyStatusNode != null && !familyStatusNode.isNull()) {
                    completedFields++;
                    missingFields.remove("familyStatus");
                }
                if (numberOfSiblingsNode != null && !numberOfSiblingsNode.isNull()) {
                    completedFields++;
                    missingFields.remove("numberOfSiblings");
                }
            }

            int percentage = (int) ((completedFields * 100.0) / totalFields);
            ProfileCompletionResponse completionResponse = new ProfileCompletionResponse(percentage, totalFields, completedFields, missingFields);
            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Profile completion calculated successfully");
            response.setData(completionResponse);
            return response;
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error calculating profile completion: " + e.getMessage());
            return response;
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