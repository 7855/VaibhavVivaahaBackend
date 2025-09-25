package com.uravugal.matrimony.controllers;

import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.services.ViewedProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/view")
public class ViewedProfileController {
     
    @Autowired
    private ViewedProfileService viewedProfileService;

    @PostMapping("/viewedProfile/{viewerId}/{viewedUserId}")
    public ResultResponse handleProfileView(@PathVariable String viewerId, @PathVariable Long viewedUserId) {
        ResultResponse response = new ResultResponse();
        try {
            viewedProfileService.handleProfileView(viewerId, viewedUserId);
            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Profile view recorded successfully");
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Error recording profile view: " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    @GetMapping("/getProfileViewers/{encodedUserId}")
    public ResultResponse getProfileViewers(@PathVariable String encodedUserId) {
        ResultResponse response = new ResultResponse();
        try {
            response = viewedProfileService.getAllViewers(encodedUserId);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching profile viewers: " + e.getMessage());
        }
        return response;
    }
}
