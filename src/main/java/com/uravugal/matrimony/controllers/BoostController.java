package com.uravugal.matrimony.controllers;

import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.services.BoostService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/boost")
@CrossOrigin(origins = "*")
public class BoostController {

    @Autowired
    private BoostService boostService;

    /** Activate a 24h profile boost. Body: { "source": "MONTHLY_CREDIT" } */
    @PostMapping("/start/{encodedUserId}")
    public ResultResponse startBoost(
            @PathVariable String encodedUserId,
            @RequestBody Map<String, String> body) {
        String source = body != null ? body.getOrDefault("source", "MONTHLY_CREDIT") : "MONTHLY_CREDIT";
        return boostService.startBoost(encodedUserId, source);
    }

    /** Get boost status + remaining credits for the profile page. */
    @GetMapping("/status/{encodedUserId}")
    public ResultResponse getBoostStatus(@PathVariable String encodedUserId) {
        return boostService.getBoostStatus(encodedUserId);
    }

    // ── Admin endpoints ──

    /** Admin: list all boosts with user details, optionally filtered by status. */
    @GetMapping("/admin/list")
    public com.uravugal.matrimony.dtos.PaginatedResultResponse adminList(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return boostService.adminList(status, page, size);
    }

    /** Admin: manually grant boost credits to a user. */
    @PostMapping("/admin/grant")
    public ResultResponse adminGrant(@RequestBody Map<String, Object> body) {
        Long userId = body.get("userId") != null ? Long.parseLong(body.get("userId").toString()) : null;
        Integer credits = body.get("credits") != null ? Integer.parseInt(body.get("credits").toString()) : 1;
        return boostService.adminGrantCredits(userId, credits);
    }

    /** Admin: instantly activate a 24h boost for a user, bypassing subscription/credits entirely. */
    @PostMapping("/admin/activate")
    public ResultResponse adminActivate(@RequestBody Map<String, Object> body) {
        Long userId = body.get("userId") != null ? Long.parseLong(body.get("userId").toString()) : null;
        return boostService.adminActivateBoost(userId);
    }

    /** Admin: revoke (expire) an active boost immediately. */
    @PostMapping("/admin/revoke/{boostId}")
    public ResultResponse adminRevoke(@PathVariable Long boostId) {
        return boostService.adminRevokeBoost(boostId);
    }
}