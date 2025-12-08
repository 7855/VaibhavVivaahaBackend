package com.uravugal.matrimony.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.uravugal.matrimony.dtos.MatchRequestDto;
import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.services.PoruthamService;

@RestController
@RequestMapping("/matching")
public class MatchingController {

    @Autowired
    private PoruthamService poruthamService;

    @PostMapping("/porutham")
    public ResultResponse poruthamMatching(@RequestBody MatchRequestDto matchRequestDto, @RequestParam(name="persist", defaultValue = "true") boolean persist) {
        ResultResponse result = new ResultResponse();
        try {
           result = poruthamService.evaluate(matchRequestDto,persist);
        } catch (Exception e) {
            e.printStackTrace();
            result.setCode(500);
            result.setMessage(e.getMessage());
            result.setMessage("Something went wrong");
            return result;
        }
        return result;
    }
}
