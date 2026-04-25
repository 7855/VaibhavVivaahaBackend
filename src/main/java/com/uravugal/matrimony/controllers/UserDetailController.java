package com.uravugal.matrimony.controllers;

import com.uravugal.matrimony.dtos.*;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.UserDetailEntity;
import com.uravugal.matrimony.services.UserDetailService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user-details")
public class UserDetailController {

    @Autowired
    private UserDetailService userDetailService;

    @PostMapping("/update-astrology-info")
    public ResultResponse updateAstrologyInfo(@Valid @RequestBody AstrologyInfoRequest request) {
        ResultResponse resp = new ResultResponse();
        try {
            userDetailService.updateAstrologyInfo(request);
            resp.setCode(200);
            resp.setMessage("Astrology information updated successfully");
            resp.setStatus(ResponseStatus.SUCCESS);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Error updating astrology information: " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @PostMapping("/update-personal-info")
    public ResultResponse updatePersonalInfo(@Valid @RequestBody PersonalInfoRequest request) {
        ResultResponse resp = new ResultResponse();
        try {
            userDetailService.updatePersonalInfo(request);
            resp.setCode(200);
            resp.setMessage("Personal information updated successfully");
            resp.setStatus(ResponseStatus.SUCCESS);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Error updating personal information: " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @PostMapping("/update-education-info")
    public ResultResponse updateEducationInfo(@Valid @RequestBody EducationInfoRequest request) {
        ResultResponse resp = new ResultResponse();
        try {
            userDetailService.updateEducationInfo(request);
            resp.setCode(200);
            resp.setMessage("Education information updated successfully");
            resp.setStatus(ResponseStatus.SUCCESS);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Error updating education information: " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @PostMapping("/update-family-info")
    public ResultResponse updateFamilyInfo(@Valid @RequestBody FamilyInfoRequest request) {
        ResultResponse resp = new ResultResponse();
        try {
            userDetailService.updateFamilyInfo(request);
            resp.setCode(200);
            resp.setMessage("Family information updated successfully");
            resp.setStatus(ResponseStatus.SUCCESS);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Error updating family information: " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @PostMapping("/updateAboutByUserId")
    public ResultResponse updateAbout(@RequestBody UserDetailEntity userDetailEntity) {
        ResultResponse resp = new ResultResponse();
        try {
            if (userDetailEntity.getUserId() == null || userDetailEntity.getAbout() == null) {
                resp.setCode(400);
                resp.setMessage("userId and about are required");
                resp.setStatus(ResponseStatus.FAILURE);
                return resp;
            }
            
            userDetailService.updateAbout(userDetailEntity.getUserId(), userDetailEntity.getAbout());
            resp.setCode(200);
            resp.setMessage("About information updated successfully");
            resp.setStatus(ResponseStatus.SUCCESS);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Error updating about information: " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    // ── Interest / Hobbies endpoints ──

    /** Get a user's hobbies as a JSON array of codes. */
    @GetMapping("/hobbies/{encodedUserId}")
    public ResultResponse getHobbies(@PathVariable String encodedUserId) {
        return userDetailService.getHobbies(encodedUserId);
    }

    /** Update a user's hobbies. Body: { "hobbies": ["food","music","cricket"] } */
    @PostMapping("/hobbies/{encodedUserId}")
    public ResultResponse updateHobbies(
            @PathVariable String encodedUserId,
            @RequestBody java.util.Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        java.util.List<String> hobbies = (java.util.List<String>) body.get("hobbies");
        return userDetailService.updateHobbies(encodedUserId, hobbies);
    }

    /** Get common interests between two users. */
    @GetMapping("/commonInterests/{viewerEncodedId}/{profileUserId}")
    public ResultResponse getCommonInterests(
            @PathVariable String viewerEncodedId,
            @PathVariable Long profileUserId) {
        return userDetailService.getCommonInterests(viewerEncodedId, profileUserId);
    }

    /** Get profiles ranked by shared interests for the home page shelf. */
    @GetMapping("/interestMatches/{encodedUserId}")
    public ResultResponse getInterestBasedMatches(
            @PathVariable String encodedUserId,
            @RequestParam(defaultValue = "10") int limit) {
        return userDetailService.getInterestBasedMatches(encodedUserId, limit);
    }

    @GetMapping("/getProfileCompletion/{userIdStr}")
    public ResultResponse getProfileCompletion(@PathVariable String userIdStr) {
        ResultResponse response = new ResultResponse();
        try {
            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Profile completion fetched successfully");
            response.setData(userDetailService.calculateProfileCompletion(userIdStr));
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching profile completion: " + e.getMessage());
        }
        return response;
    }
}
