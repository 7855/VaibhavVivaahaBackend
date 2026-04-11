package com.uravugal.matrimony.controllers;

import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.CasteEntity;
import com.uravugal.matrimony.services.CasteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/caste")
public class CasteController {

    @Autowired
    private CasteService casteService;

    @GetMapping("/getAllActiveCaste")
    public ResultResponse getAll() {
        ResultResponse resp = new ResultResponse();
        try {
            resp = casteService.getAllByActiveStatus();
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @PostMapping("/create")
    public ResultResponse createCaste(@RequestBody CasteEntity caste) {
        ResultResponse resp = new ResultResponse();
        try {
            resp = casteService.createCaste(caste);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @PutMapping("/update/{id}")
    public ResultResponse updateCaste(@PathVariable Long id, @RequestBody CasteEntity caste) {
        ResultResponse resp = new ResultResponse();
        try {
            resp = casteService.updateCaste(id, caste);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }
}
