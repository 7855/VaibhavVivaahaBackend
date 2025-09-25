package com.uravugal.matrimony.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.services.HiddenFieldService;

@RestController
@RequestMapping("/hiddenFields")
public class HiddenFieldController {
    
    @Autowired
    private HiddenFieldService hiddenFieldService;

    @PostMapping("/createHiddenField/{userId}/{fieldName}")
    public ResultResponse  createHiddenField(@PathVariable String userId, @PathVariable String fieldName) {
        try {
            ResultResponse response = hiddenFieldService.createHideField(userId, fieldName);
            return response;
        } catch (Exception e) {
            ResultResponse errorResponse = new ResultResponse();
            errorResponse.setCode(500);
            errorResponse.setStatus(ResponseStatus.FAILURE);
            errorResponse.setMessage("Error hiding field: " + e.getMessage());
            return errorResponse;
        }
    }

    @DeleteMapping("/deleteHiddenField/{hiddenFieldId}")
    public ResultResponse deleteHiddenField(@PathVariable Long hiddenFieldId) {
        try {
            ResultResponse response = hiddenFieldService.unhideField(hiddenFieldId);
            return response;
        } catch (Exception e) {
            ResultResponse errorResponse = new ResultResponse();
            errorResponse.setCode(500);
            errorResponse.setStatus(ResponseStatus.FAILURE);
            errorResponse.setMessage("Error unhiding field: " + e.getMessage());
            return errorResponse;
        }
    }

    @GetMapping("/getHiddenFieldsByUserId/{userId}")
    public ResultResponse getHiddenFieldsByUserId(@PathVariable Long userId) {
        try {
            ResultResponse response = hiddenFieldService.getHiddenFieldsByUserId(userId);
            return response;
        } catch (Exception e) {
            ResultResponse errorResponse = new ResultResponse();
            errorResponse.setCode(500);
            errorResponse.setStatus(ResponseStatus.FAILURE);
            errorResponse.setMessage("Error fetching hidden fields: " + e.getMessage());
            return errorResponse;
        }
    }

}
