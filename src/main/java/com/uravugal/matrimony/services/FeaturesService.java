package com.uravugal.matrimony.services;

import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.Features;
import com.uravugal.matrimony.repositories.FeaturesRepository;
import com.uravugal.matrimony.repositories.PlanFeaturesRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class FeaturesService {

    @Autowired
    private FeaturesRepository featuresRepository;

    @Autowired
    private PlanFeaturesRepository planFeaturesRepository;

    public ResultResponse getAllFeatures() {
        ResultResponse resp = new ResultResponse();
        try {
            List<Features> all = featuresRepository.findAll();
            all.sort((a, b) -> a.getCode().compareToIgnoreCase(b.getCode()));
            resp.setCode(200);
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setMessage("Features fetched");
            resp.setData(all);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setStatus(ResponseStatus.FAILURE);
            resp.setMessage("Error fetching features: " + e.getMessage());
        }
        return resp;
    }

    public ResultResponse createFeature(Features input) {
        ResultResponse resp = new ResultResponse();
        try {
            if (input.getCode() == null || input.getCode().isBlank()) {
                resp.setCode(400);
                resp.setStatus(ResponseStatus.FAILURE);
                resp.setMessage("code is required");
                return resp;
            }
            if (featuresRepository.findByCode(input.getCode().trim().toUpperCase()) != null) {
                resp.setCode(409);
                resp.setStatus(ResponseStatus.FAILURE);
                resp.setMessage("A feature with this code already exists");
                return resp;
            }
            Features feature = new Features();
            feature.setCode(input.getCode().trim().toUpperCase());
            feature.setName(input.getName());
            feature.setDescription(input.getDescription());
            Features saved = featuresRepository.save(feature);

            resp.setCode(200);
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setMessage("Feature created");
            resp.setData(saved);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setStatus(ResponseStatus.FAILURE);
            resp.setMessage("Error creating feature: " + e.getMessage());
        }
        return resp;
    }

    public ResultResponse updateFeature(Long id, Features input) {
        ResultResponse resp = new ResultResponse();
        try {
            Optional<Features> opt = featuresRepository.findById(id);
            if (opt.isEmpty()) {
                resp.setCode(404);
                resp.setStatus(ResponseStatus.FAILURE);
                resp.setMessage("Feature not found");
                return resp;
            }
            Features feature = opt.get();
            // code is intentionally NOT editable here — it's what every UserService/etc. lookup
            // (findByCode("VIEW_PERSONAL_INFO"), etc.) is hardcoded against; renaming it would
            // silently break every feature check across the backend with no compile-time warning.
            if (input.getName() != null) feature.setName(input.getName());
            if (input.getDescription() != null) feature.setDescription(input.getDescription());
            Features saved = featuresRepository.save(feature);

            resp.setCode(200);
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setMessage("Feature updated");
            resp.setData(saved);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setStatus(ResponseStatus.FAILURE);
            resp.setMessage("Error updating feature: " + e.getMessage());
        }
        return resp;
    }

    public ResultResponse deleteFeature(Long id) {
        ResultResponse resp = new ResultResponse();
        try {
            Optional<Features> opt = featuresRepository.findById(id);
            if (opt.isEmpty()) {
                resp.setCode(404);
                resp.setStatus(ResponseStatus.FAILURE);
                resp.setMessage("Feature not found");
                return resp;
            }
            // Refuse deletion while any plan still has this feature attached — otherwise the
            // planFeatures rows become orphaned foreign keys pointing at nothing, and any code
            // that does featuresRepository.findByCode(...) then looks up planFeatures by its id
            // silently stops matching instead of failing loudly.
            long attachedCount = planFeaturesRepository.findAll().stream()
                    .filter(pf -> pf.getFeatureId().equals(id))
                    .count();
            if (attachedCount > 0) {
                resp.setCode(409);
                resp.setStatus(ResponseStatus.FAILURE);
                resp.setMessage("Feature is attached to " + attachedCount + " plan(s) — detach it from all plans first");
                return resp;
            }
            featuresRepository.deleteById(id);
            resp.setCode(200);
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setMessage("Feature deleted");
        } catch (Exception e) {
            resp.setCode(500);
            resp.setStatus(ResponseStatus.FAILURE);
            resp.setMessage("Error deleting feature: " + e.getMessage());
        }
        return resp;
    }
}
