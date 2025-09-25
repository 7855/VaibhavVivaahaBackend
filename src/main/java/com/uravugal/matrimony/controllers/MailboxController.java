package com.uravugal.matrimony.controllers;

import com.uravugal.matrimony.dtos.PaginatedResultResponse;
import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.ShortlistedProfile;
import com.uravugal.matrimony.services.InterestRequestService;
import com.uravugal.matrimony.services.RestrictedFieldRequestService;
import com.uravugal.matrimony.services.ShortlistedProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/mailbox")
public class MailboxController {

    @Autowired
    private InterestRequestService interestRequestService;

    @Autowired
    private RestrictedFieldRequestService restrictedFieldRequestService;

    @Autowired
    private ShortlistedProfileService shortlistedProfileService;

    @GetMapping("/received/{encodedId}")
    public PaginatedResultResponse getReceivedRequests(
        @PathVariable String encodedId,
        @RequestParam(defaultValue = "0") Integer page,
        @RequestParam(defaultValue = "10") Integer size
    ) {
        return interestRequestService.getReceivedRequests(encodedId, page, size);
    }

    @GetMapping("/sent/{encodedId}")
    public PaginatedResultResponse getSentRequests(
        @PathVariable String encodedId,
        @RequestParam(defaultValue = "0") Integer page,
        @RequestParam(defaultValue = "10") Integer size
    ) {
        return interestRequestService.getSentRequests(encodedId, page, size);
    }

    @GetMapping("/restricted-field-requests/{encodedId}")
    public PaginatedResultResponse getRestrictedFieldRequests(
        @PathVariable String encodedId,
        @RequestParam(defaultValue = "0") Integer page,
        @RequestParam(defaultValue = "10") Integer size
    ) {
        return restrictedFieldRequestService.getReceivedRequests(encodedId, page, size);
    }

    @GetMapping("/shortlisted/{encodedId}")
    public PaginatedResultResponse getShortlistedProfiles(
        @PathVariable String encodedId,
        @RequestParam(defaultValue = "0") Integer page,
        @RequestParam(defaultValue = "10") Integer size
    ) {
        return shortlistedProfileService.getShortlistedProfiles(encodedId, page, size);
    }

    @PostMapping("/insertShortlistedProfile")
    public ResultResponse insertShortlistedProfile(@RequestBody ShortlistedProfile shortlistedProfile) {
        ResultResponse response = new ResultResponse();
        try {
            response = shortlistedProfileService.insertShortlistedProfile(shortlistedProfile);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    @GetMapping("/pending-received/{encodedId}")
    public PaginatedResultResponse getPendingReceivedRequests(
            @PathVariable String encodedId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return interestRequestService.getPendingReceivedRequests(encodedId, page, size);
    }

    @GetMapping("/accepted-received/{encodedId}")
    public PaginatedResultResponse getAcceptedReceivedRequests(
            @PathVariable String encodedId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return interestRequestService.getAcceptedReceivedRequests(encodedId, page, size);
    }

    @GetMapping("/rejected/{encodedId}")
    public PaginatedResultResponse getRejectedReceivedRequests(
            @PathVariable String encodedId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return interestRequestService.getRejectedReceivedRequests(encodedId, page, size);
    }

    @GetMapping("/updateInterestRequestStatus/{id}/{approvalStatus}")
    public ResultResponse updateInterestRequestStatus(
            @PathVariable Long id,
            @PathVariable String approvalStatus) {
        return interestRequestService.updateInterestRequestStatus(id, approvalStatus);
    }

    @DeleteMapping("/deleteInterestRequest/{id}")
    public ResultResponse deleteInterestRequest(
            @PathVariable Long id) {
        return interestRequestService.deleteInterestRequest(id);
    }

    @DeleteMapping("/deleteShortlistedProfile/{id}")
    public ResultResponse deleteShortlistedProfile(
            @PathVariable Long id) {
        return shortlistedProfileService.deleteShortlistedProfile(id);
    }

    @DeleteMapping("/deleteShortlistedProfileByUsers/{encodedId}/{userId}")
    public ResultResponse deleteShortlistedProfileByUsers(
            @PathVariable String encodedId,
            @PathVariable Long userId) {
        return shortlistedProfileService.deleteShortlistedProfileByUsers(encodedId, userId);
    }

    @GetMapping("/checkShortlisted/{encodedId}/{userId}")
    public ResultResponse checkIfShortlisted(
            @PathVariable String encodedId,
            @PathVariable Long userId) {
        return shortlistedProfileService.checkIfShortlisted(encodedId, userId);
    }
}
