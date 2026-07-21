package com.uravugal.matrimony.services;

import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.Features;
import com.uravugal.matrimony.models.PlanFeatures;
import com.uravugal.matrimony.models.SubscriptionPlan;
import com.uravugal.matrimony.repositories.FeaturesRepository;
import com.uravugal.matrimony.repositories.PlanFeaturesRepository;
import com.uravugal.matrimony.repositories.SubscriptionPlanRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PlanFeaturesService {

    @Autowired
    private PlanFeaturesRepository planFeaturesRepository;

    @Autowired
    private FeaturesRepository featuresRepository;

    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;

    /**
     * Everything the admin matrix UI needs in one call: every plan, every feature, and every
     * existing planFeatures row — the frontend pivots this into a features-x-plans grid itself,
     * same "return everything, enrich, let the client pivot" shape used elsewhere in this admin API.
     */
    public ResultResponse getMatrix() {
        ResultResponse resp = new ResultResponse();
        try {
            List<SubscriptionPlan> plans = subscriptionPlanRepository.findAll();
            plans.sort((a, b) -> a.getPrice().compareTo(b.getPrice()));

            List<Features> features = featuresRepository.findAll();
            features.sort((a, b) -> a.getCode().compareToIgnoreCase(b.getCode()));

            List<PlanFeatures> cells = planFeaturesRepository.findAll();

            Map<String, Object> data = new HashMap<>();
            data.put("plans", plans);
            data.put("features", features);
            data.put("cells", cells);

            resp.setCode(200);
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setMessage("Plan feature matrix fetched");
            resp.setData(data);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setStatus(ResponseStatus.FAILURE);
            resp.setMessage("Error fetching plan feature matrix: " + e.getMessage());
        }
        return resp;
    }

    /**
     * Attach a feature to a plan, or update its limit if already attached — a plan+feature pair
     * is unique, so this is always an upsert, never a plain insert (mirrors how every other
     * feature check in this codebase does `findByFeatureIdAndSubscriptionPlanId` first).
     */
    public ResultResponse upsertCell(Long subscriptionPlanId, Long featureId, String limitValue, String limitPeriod) {
        ResultResponse resp = new ResultResponse();
        try {
            if (subscriptionPlanId == null || featureId == null) {
                resp.setCode(400);
                resp.setStatus(ResponseStatus.FAILURE);
                resp.setMessage("subscriptionPlanId and featureId are required");
                return resp;
            }
            PlanFeatures existing = planFeaturesRepository.findByFeatureIdAndSubscriptionPlanId(featureId, subscriptionPlanId);
            PlanFeatures pf = existing != null ? existing : new PlanFeatures();
            pf.setSubscriptionPlanId(subscriptionPlanId);
            pf.setFeatureId(featureId);
            pf.setLimitValue(limitValue);
            pf.setLimitPeriod(limitPeriod);
            PlanFeatures saved = planFeaturesRepository.save(pf);

            resp.setCode(200);
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setMessage(existing != null ? "Plan feature updated" : "Plan feature attached");
            resp.setData(saved);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setStatus(ResponseStatus.FAILURE);
            resp.setMessage("Error saving plan feature: " + e.getMessage());
        }
        return resp;
    }

    /** Detach a feature from a plan entirely (removes the cell, not just clears its value). */
    public ResultResponse deleteCell(Long id) {
        ResultResponse resp = new ResultResponse();
        try {
            Optional<PlanFeatures> opt = planFeaturesRepository.findById(id);
            if (opt.isEmpty()) {
                resp.setCode(404);
                resp.setStatus(ResponseStatus.FAILURE);
                resp.setMessage("Plan feature not found");
                return resp;
            }
            planFeaturesRepository.deleteById(id);
            resp.setCode(200);
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setMessage("Plan feature detached");
        } catch (Exception e) {
            resp.setCode(500);
            resp.setStatus(ResponseStatus.FAILURE);
            resp.setMessage("Error detaching plan feature: " + e.getMessage());
        }
        return resp;
    }
}
