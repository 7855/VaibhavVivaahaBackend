package com.uravugal.matrimony.controllers;

import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.models.Features;
import com.uravugal.matrimony.services.FeaturesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/features")
public class FeaturesController {

    @Autowired
    private FeaturesService featuresService;

    @GetMapping("/getAllFeatures")
    public ResultResponse getAllFeatures() {
        return featuresService.getAllFeatures();
    }

    @PostMapping("/createFeature")
    public ResultResponse createFeature(@RequestBody Features feature) {
        return featuresService.createFeature(feature);
    }

    @PutMapping("/updateFeature/{id}")
    public ResultResponse updateFeature(@PathVariable Long id, @RequestBody Features feature) {
        return featuresService.updateFeature(id, feature);
    }

    @DeleteMapping("/deleteFeature/{id}")
    public ResultResponse deleteFeature(@PathVariable Long id) {
        return featuresService.deleteFeature(id);
    }
}
