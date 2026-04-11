package com.uravugal.matrimony.controllers;

import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.models.FamilyLogin;
import com.uravugal.matrimony.services.FamilyLoginService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/family-login")
public class FamilyLoginController {

    @Autowired
    private FamilyLoginService familyLoginService;

    @PostMapping("/create/{encodedUserId}")
    public ResultResponse create(
            @PathVariable String encodedUserId,
            @RequestBody FamilyLogin body) {
        return familyLoginService.createFamilyLogin(encodedUserId, body);
    }

    @GetMapping("/mine/{encodedUserId}")
    public ResultResponse listMine(@PathVariable String encodedUserId) {
        return familyLoginService.listMine(encodedUserId);
    }

    @DeleteMapping("/{encodedUserId}/{familyLoginId}")
    public ResultResponse revoke(
            @PathVariable String encodedUserId,
            @PathVariable Long familyLoginId) {
        return familyLoginService.revoke(encodedUserId, familyLoginId);
    }

    // ---------- Admin ----------
    @GetMapping("/admin/list")
    public ResultResponse adminList() {
        return familyLoginService.adminList();
    }

    @DeleteMapping("/admin/{id}")
    public ResultResponse adminRevoke(@PathVariable Long id) {
        return familyLoginService.adminRevoke(id);
    }
}