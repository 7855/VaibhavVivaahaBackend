package com.uravugal.matrimony.controllers;

import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.dtos.UserLikesRequest;
import com.uravugal.matrimony.models.UserLikes;
import com.uravugal.matrimony.services.UserLikesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/userLikes")
public class UserLikesController {
    
    @Autowired
    private UserLikesService userLikesService;

    @PostMapping("/createUserLike")
    public ResultResponse createUserLike(@RequestBody UserLikesRequest userLike) {
        return userLikesService.createUserLike(userLike);
    }

    @GetMapping("/checkIfLiked/{likedBy}/{likedTo}")
public ResultResponse checkIfLiked(
    @PathVariable String likedBy,
    @PathVariable Long likedTo) {
    return userLikesService.checkIfLiked(likedBy, likedTo);
}

@DeleteMapping("/deleteLike/{likedBy}/{likedTo}")
public ResultResponse deleteLike(
    @PathVariable String likedBy,
    @PathVariable Long likedTo) {
    return userLikesService.deleteLike(likedBy, likedTo);
}
}