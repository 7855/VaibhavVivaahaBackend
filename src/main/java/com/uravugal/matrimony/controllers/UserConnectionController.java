package com.uravugal.matrimony.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.services.UserConnectionService;

@RestController
@RequestMapping("/userConnection")
public class UserConnectionController {

    @Autowired
    private UserConnectionService userConnectionService;

    @GetMapping("/connectionCount/{encodedId}")
    public ResultResponse getConnectionCount(@PathVariable String encodedId) {
        return userConnectionService.getConnectionCount(encodedId);
    }

    @GetMapping("/acceptedConnections/{encodedId}")
    public ResultResponse getAcceptedConnections(@PathVariable String encodedId) {
        return userConnectionService.getAcceptedConnections(encodedId);
    }

    @GetMapping("/followers/{encodedId}")
    public ResultResponse getFollowersList(@PathVariable String encodedId) {
        ResultResponse resp = new ResultResponse();
        try {
            resp = userConnectionService.getFollowersList(encodedId);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @GetMapping("/following/{encodedId}")
    public ResultResponse getFollowingList(@PathVariable String encodedId) {
        ResultResponse resp = new ResultResponse();
        try {
            resp = userConnectionService.getFollowingList(encodedId);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @PostMapping("/unfollow/{followerId}/{followingId}")
    public ResultResponse unfollowUser(@PathVariable Long followerId, @PathVariable Long followingId) {
        ResultResponse resp = new ResultResponse();
        try {
            resp = userConnectionService.unfollowUser(followerId, followingId);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }
}
