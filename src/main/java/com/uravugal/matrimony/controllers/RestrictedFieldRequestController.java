package com.uravugal.matrimony.controllers;

import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ApprovalStatus;
import com.uravugal.matrimony.services.RestrictedFieldRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/restrictedFieldRequest")
public class RestrictedFieldRequestController {

    @Autowired
    private RestrictedFieldRequestService restrictedFieldRequestService;

    @GetMapping("/getRestrictedRequestsById/{id}")
    public ResultResponse getRestrictedRequestsById(
            @PathVariable String id) {
        return restrictedFieldRequestService.getRestrictedRequestsById(id);
    }
    
    @PutMapping("/updateStatus/{id}")
    public ResultResponse updateRestrictedFieldStatus(
            @PathVariable String id,
            @RequestParam ApprovalStatus status) {
        return restrictedFieldRequestService.updateRestrictedFieldStatus(id, status);
    }

    @GetMapping("/getRestrictedRequestsToId/{id}")
    public ResultResponse getRestrictedRequestsToId(
            @PathVariable String id) {
        return restrictedFieldRequestService.getRestrictedRequestsToId(id);
    }

    @PostMapping("/sendRestrictedFieldRequest/{requestedBy}/{requestedTo}/{fieldType}")
    public ResultResponse sendRestrictedFieldRequest(
            @PathVariable Long requestedBy,
            @PathVariable Long requestedTo,
            @PathVariable String fieldType) {
        return restrictedFieldRequestService.sendRestrictedFieldRequest(requestedBy, requestedTo, fieldType);
    }

    @DeleteMapping("/deleteRequest/{requestedBy}/{requestedTo}/{fieldType}")
    public ResultResponse deleteRequest(
            @PathVariable Long requestedBy,
            @PathVariable Long requestedTo,
            @PathVariable String fieldType) {
        return restrictedFieldRequestService.deleteRequest(requestedBy, requestedTo, fieldType);
    }

    @GetMapping("/getRequestsTo/{requestBy}/{requestTo}")
    public ResultResponse getRequestsToIds(
            @PathVariable Long requestBy,
            @PathVariable Long requestTo) {
        return restrictedFieldRequestService.getRequestsToIds(requestBy, requestTo);
    }
    
}
