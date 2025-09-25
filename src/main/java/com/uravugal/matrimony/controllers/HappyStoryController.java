package com.uravugal.matrimony.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ActiveStatus;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.services.HappyStoryService;

@RestController
@RequestMapping("/happyStory")
public class HappyStoryController {
    
    @Autowired
    private HappyStoryService happyStoryService;

    @GetMapping("/getAllHappyStoriesByIsActive/{isActive}")
    public ResultResponse getAllHappyStoriesByIsActive(@PathVariable ActiveStatus isActive) {
        ResultResponse response = new ResultResponse();
        try {
            response = happyStoryService.getAllHappyStoriesByIsActive(isActive);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    @GetMapping("/getAllHappyStories")
    public ResultResponse getAllHappyStories() {
        ResultResponse response = new ResultResponse();
        try {
            response = happyStoryService.getAllHappyStories();
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    @PostMapping("/changeHappyStoryActiveStatus/{happystoryId}")
    public ResultResponse changeHappyStoryActiveStatus(@PathVariable String happystoryId, 
                                                      @RequestParam Boolean isActive) {
        ResultResponse response = new ResultResponse();
        try {
            response = happyStoryService.changeHappyStoryActiveStatus(happystoryId, isActive);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    @GetMapping("/getByHappyStoryId/{happystoryId}")
    public ResultResponse getByHappyStoryId(@PathVariable String happystoryId) {
        ResultResponse response = new ResultResponse();
        try {
            response = happyStoryService.getByHappyStoryId(happystoryId);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }
}
