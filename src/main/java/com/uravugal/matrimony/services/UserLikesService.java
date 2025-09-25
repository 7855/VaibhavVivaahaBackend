package com.uravugal.matrimony.services;

import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.dtos.UserLikesRequest;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.UserLikes;
import com.uravugal.matrimony.repositories.UserLikesRepository;

import java.util.Base64;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserLikesService {
    
    @Autowired
    private UserLikesRepository userLikesRepository;

    public ResultResponse createUserLike(UserLikesRequest userLike) {
        ResultResponse response = new ResultResponse();
        try {
            // Decode the Base64 encoded IDs
            String decodedId = new String(Base64.getDecoder().decode(userLike.getLikedBy()));
            Long likerUserId = Long.parseLong(decodedId);
            
            // Check if like already exists
            boolean alreadyLiked = userLikesRepository.existsByLikerAndLikedUser(likerUserId, 
                Long.parseLong(userLike.getLikedTo()));
            
            if (alreadyLiked) {
                response.setCode(400);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("User has already liked this profile");
                return response;
            }
    
            // Create new like if it doesn't exist
            UserLikes userLikeRequest = new UserLikes();
            userLikeRequest.setLikedBy(likerUserId);
            userLikeRequest.setLikedTo(Long.parseLong(userLike.getLikedTo()));
    
            UserLikes savedLike = userLikesRepository.save(userLikeRequest);
            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Like created successfully");
            response.setData(savedLike);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error creating like: " + e.getMessage());
        }
        return response;
    }

    public ResultResponse checkIfLiked(String likedBy, Long likedTo) {
        ResultResponse response = new ResultResponse();
        try {
            String decodedId = new String(Base64.getDecoder().decode(likedBy));
            Long likerUserId = Long.parseLong(decodedId);
            boolean isLiked = userLikesRepository.existsByLikerAndLikedUser(likerUserId, likedTo);
            System.out.println("isLiked: ============================>" + isLiked);
            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Like check completed");
            response.setData(isLiked);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error checking like status: " + e.getMessage());
        }
        return response;
    }

    public ResultResponse deleteLike(String likedBy, Long likedTo) {
        ResultResponse response = new ResultResponse();
        try {
            String decodedId = new String(Base64.getDecoder().decode(likedBy));
            Long likerUserId = Long.parseLong(decodedId);
            
            // Check if like exists before deleting
            System.out.println("likerUserId: ============================>" + likerUserId);
            System.out.println("likedTo: ============================>" + likedTo);
            boolean exists = userLikesRepository.existsByLikerAndLikedUser(likerUserId, likedTo);
            System.out.println("exists: ============================>" + exists);
            if (!exists) {
                response.setCode(404);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("Like does not exist");
                return response;
            }
            
            // Delete the like
            userLikesRepository.deleteByLikerAndLikedUser(likerUserId, likedTo);
            
            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Like removed successfully");
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error removing like: " + e.getMessage());
        }
        return response;
    }
}