package com.uravugal.matrimony.services;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ActiveStatus;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.HappyStoryEntity;
import com.uravugal.matrimony.repositories.HappyStoryRepository;

@Service
public class HappyStoryService {
    
    @Autowired
    private HappyStoryRepository happyStoryRepository;

    public ResultResponse getAllHappyStoriesByIsActive(ActiveStatus isActive) {
        ResultResponse response = new ResultResponse();
        try {
            List<HappyStoryEntity> stories = happyStoryRepository.findByIsActive(isActive);
            response.setData(stories);
            response.setCode(200);
            response.setMessage("Happy stories retrieved successfully");
            response.setStatus(ResponseStatus.SUCCESS);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    public ResultResponse getAllHappyStories() {
        ResultResponse response = new ResultResponse();
        try {
            List<HappyStoryEntity> stories = happyStoryRepository.findAll();
            response.setData(stories);
            response.setCode(200);
            response.setMessage("All happy stories retrieved successfully");
            response.setStatus(ResponseStatus.SUCCESS);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    public ResultResponse changeHappyStoryActiveStatus(String happystoryId, Boolean isActive) {
        ResultResponse response = new ResultResponse();
        try {
            HappyStoryEntity story = happyStoryRepository.findByHappystoryId(happystoryId);
            if (story != null) {
                story.setIsActive(isActive ? ActiveStatus.Y : ActiveStatus.N);
                happyStoryRepository.save(story);
                response.setCode(200);
                response.setMessage("Happy story status updated successfully");
                response.setStatus(ResponseStatus.SUCCESS);
            } else {
                response.setCode(404);
                response.setMessage("Happy story not found");
                response.setStatus(ResponseStatus.FAILURE);
            }
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    public ResultResponse getByHappyStoryId(String happystoryId) {
        ResultResponse response = new ResultResponse();
        try {
            HappyStoryEntity story = happyStoryRepository.findByHappystoryId(happystoryId);
            if (story != null) {
                response.setData(story);
                response.setCode(200);
                response.setMessage("Happy story retrieved successfully");
                response.setStatus(ResponseStatus.SUCCESS);
            } else {
                response.setCode(404);
                response.setMessage("Happy story not found");
                response.setStatus(ResponseStatus.FAILURE);
            }
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }
}
