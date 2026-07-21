package com.uravugal.matrimony.controllers;

import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.services.PlanFeaturesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/planFeatures")
public class PlanFeaturesController {

    @Autowired
    private PlanFeaturesService planFeaturesService;

    /** Admin: everything needed to render the features-x-plans matrix in one call. */
    @GetMapping("/matrix")
    public ResultResponse getMatrix() {
        return planFeaturesService.getMatrix();
    }

    /** Admin: attach/update a feature's limit on a plan. Body: {subscriptionPlanId, featureId, limitValue, limitPeriod} */
    @PostMapping("/upsert")
    public ResultResponse upsertCell(@RequestBody Map<String, Object> body) {
        Long subscriptionPlanId = body.get("subscriptionPlanId") != null ? Long.valueOf(body.get("subscriptionPlanId").toString()) : null;
        Long featureId = body.get("featureId") != null ? Long.valueOf(body.get("featureId").toString()) : null;
        String limitValue = body.get("limitValue") != null ? body.get("limitValue").toString() : null;
        String limitPeriod = body.get("limitPeriod") != null ? body.get("limitPeriod").toString() : null;
        return planFeaturesService.upsertCell(subscriptionPlanId, featureId, limitValue, limitPeriod);
    }

    /** Admin: detach a feature from a plan. */
    @DeleteMapping("/{id}")
    public ResultResponse deleteCell(@PathVariable Long id) {
        return planFeaturesService.deleteCell(id);
    }
}
