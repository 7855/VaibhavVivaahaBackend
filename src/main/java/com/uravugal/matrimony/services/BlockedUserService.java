package com.uravugal.matrimony.services;

import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.BlockedUser;
import com.uravugal.matrimony.repositories.BlockedUserRepository;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BlockedUserService {
    
    @Autowired
    private BlockedUserRepository blockedUserRepository;

    public ResultResponse getAllBlockedUsers() {
        ResultResponse response = new ResultResponse();
        try {
            List<BlockedUser> blockedUsers = blockedUserRepository.findAllOrderByBlockedAtDesc();
            response.setCode(200);
            response.setMessage("Blocked users retrieved successfully");
            response.setStatus(ResponseStatus.SUCCESS);
            response.setData(blockedUsers);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    public ResultResponse deleteBlockedUser(Long id) {
        ResultResponse response = new ResultResponse();
        try {
            blockedUserRepository.deleteById(id);
            response.setCode(200);
            response.setMessage("Blocked user removed successfully");
            response.setStatus(ResponseStatus.SUCCESS);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    public ResultResponse checkBlockedByBlockedId(Long user1, Long user2) {
        ResultResponse response = new ResultResponse();
        try {
            BlockedUser blockedUser = blockedUserRepository.findByUsersEitherDirection(user1, user2);

            if (blockedUser != null) {
                response.setCode(200);
                response.setMessage("User is already blocked");
                response.setStatus(ResponseStatus.SUCCESS);
                response.setData(blockedUser);
            } else {
                response.setCode(404);
                response.setMessage("User is not blocked");
                response.setStatus(ResponseStatus.FAILURE);
                response.setData(blockedUser);
            }
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    public ResultResponse blockUser(Long blockedByUserId, Long blockedUserId) {
        ResultResponse response = new ResultResponse();
        try {
            // Check if already blocked
            BlockedUser existingBlock = blockedUserRepository.findByBlockedByUserIdAndBlockedUserId(
                blockedByUserId, 
                blockedUserId
            );

            if (existingBlock != null) {
                response.setCode(400);
                response.setMessage("User is already blocked");
                response.setStatus(ResponseStatus.FAILURE);
                return response;
            }

            // Create new block entry
            BlockedUser blockedUser = new BlockedUser();
            blockedUser.setBlockedByUserId(blockedByUserId);
            blockedUser.setBlockedUserId(blockedUserId);
            blockedUserRepository.save(blockedUser);

            response.setCode(200);
            response.setMessage("User blocked successfully");
            response.setStatus(ResponseStatus.SUCCESS);
            response.setData(blockedUser);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }
}
