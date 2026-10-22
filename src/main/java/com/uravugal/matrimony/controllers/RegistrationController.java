package com.uravugal.matrimony.controllers;

import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.services.RegistrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** New app registration flow (2026-10-19). Separate from /user/createUser on purpose. */
@RestController
@RequestMapping("/auth/register")
@CrossOrigin(origins = "*")
public class RegistrationController {

    @Autowired
    private RegistrationService registrationService;

    @PostMapping("/start")
    public ResultResponse start(@RequestBody Map<String, Object> body) { return registrationService.start(body); }

    @PostMapping("/step")
    public ResultResponse step(@RequestBody Map<String, Object> body) { return registrationService.saveStep(body); }

    @PostMapping("/complete")
    public ResultResponse complete(@RequestBody Map<String, Object> body) { return registrationService.complete(body); }
}
