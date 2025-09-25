package com.uravugal.matrimony.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.dtos.BlockUserRequest;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.services.BlockedUserService;
import java.util.Base64;

@RestController
@RequestMapping("/block")
public class BlockedUserController {
    
    @Autowired
    private BlockedUserService blockedUserService;

    @GetMapping("/getAllBlockedUsers")
    public ResultResponse getAllBlockedUsers() {
        ResultResponse response = new ResultResponse();
        try {
            response = blockedUserService.getAllBlockedUsers();
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    @DeleteMapping("/deleteBlockedUser/{id}")
    public ResultResponse deleteBlockedUser(@PathVariable Long id) {
        ResultResponse response = new ResultResponse();
        try {
            response = blockedUserService.deleteBlockedUser(id);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    @GetMapping("/checkBlockedByBlockedId/{user1}/{user2}")
    public ResultResponse checkBlockedByBlockedId(
        @PathVariable String user1,
        @PathVariable Long user2
    ) {
        ResultResponse response = new ResultResponse();
        try {
            String decodedId = new String(Base64.getDecoder().decode(user1));
            Long senderUserId = Long.parseLong(decodedId);
            response = blockedUserService.checkBlockedByBlockedId(senderUserId, user2);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    @PostMapping("/blockUser")
    public ResultResponse blockUser(@RequestBody BlockUserRequest blockRequest) {
        ResultResponse response = new ResultResponse();
        try {
            String decodedId = new String(Base64.getDecoder().decode(blockRequest.getBlockedByUserId()));
            Long senderUserId = Long.parseLong(decodedId);

            response = blockedUserService.blockUser(senderUserId, blockRequest.getBlockedUserId());
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }
}
