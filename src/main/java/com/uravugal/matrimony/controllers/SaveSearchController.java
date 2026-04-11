package com.uravugal.matrimony.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.SavedSearchesEntity;
import com.uravugal.matrimony.services.SaveSearchService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/savedSearches")
public class SaveSearchController {

    @Autowired
    private SaveSearchService saveSearchService;

    @GetMapping("/getUserSavedSearches/{userId}")
    public ResultResponse getUserSavedSearches(@PathVariable Long userId) {
        ResultResponse result = new ResultResponse();
        try {
            result = saveSearchService.getUserSavedSearches(userId);
        } catch (Exception e) {
            result.setCode(500);
            result.setMessage("Something Went Wrong");
            result.setStatus(ResponseStatus.FAILURE);
        }
        return result;
    }

    @PostMapping("/createOrUpdateSavedSearch")
    public ResultResponse createOrUpdateSavedSearch(
            @RequestBody Map<String, Object> request) {
        ResultResponse result = new ResultResponse();
        try {
            // Extract the data from the request
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) request.get("data");

            System.out.println("data=>" + data);

            if (data == null) {
                throw new IllegalArgumentException("Request data cannot be null");
            }

            // Create the saved search entity
            SavedSearchesEntity savedSearch = new SavedSearchesEntity();
            savedSearch.setSearchName((String) data.get("searchName"));
            savedSearch.setUserId(Long.parseLong(data.get("userId").toString()));

            // Get the filters array
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> filters = (List<Map<String, Object>>) data.get("filters");

            // Convert filters array to the expected map format
            Map<String, Object> filterMap = new HashMap<>();
            for (Map<String, Object> filter : filters) {
                String key = (String) filter.get("filterKey");
                Object value = filter.get("filterValue");

                // Convert the key to match your expected parameter names
                switch (key) {
                    case "Age":
                        // Save age range as a single string "min - max"
                        filterMap.put("ageRange", value);
                        break;
                    case "Annual Income":
                        // Save income range as a single string "min - max"
                        filterMap.put("incomeRange", value);
                        break;
                    case "Dosham":
                        filterMap.put("dosham", value);
                        break;
                    case "Star":
                        filterMap.put("star", value);
                        break;
                    case "Education":
                        filterMap.put("degree", value);
                        break;
                    case "Job Sector":
                        if (value instanceof List) {
                            try {
                                @SuppressWarnings("unchecked")
                                List<String> jobSectorList = (List<String>) value;
                                String jobSectors = String.join(",", jobSectorList);
                                filterMap.put("jobSectors", jobSectors);
                            } catch (ClassCastException e) {
                                // Handle case where the list doesn't contain Strings
                                String jobSectors = ((List<?>) value).stream()
                                        .map(Object::toString)
                                        .collect(Collectors.joining(","));
                                filterMap.put("jobSectors", jobSectors);
                            }
                        } else {
                            filterMap.put("jobSectors", value.toString());
                        }
                        break;
                    case "profileImageStatus1":
                        filterMap.put("profileImageStatus", value);
                        break;
                    case "profilesWithHoroscope":
                        filterMap.put("profilesWithHoroscope", value);
                        break;
                }
            }

            System.out.println("Processed filters: " + filterMap);
            result = saveSearchService.createOrUpdateSavedSearch(savedSearch, filterMap);
        } catch (Exception e) {
            e.printStackTrace();
            result.setCode(500);
            result.setMessage("Something Went Wrong: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
            result.setStatus(ResponseStatus.FAILURE);
        }
        return result;
    }

    @PutMapping("/inActiveSavedSearch/{id}")
    public ResultResponse deleteSavedSearch(
            @PathVariable Long id) {
        ResultResponse result = new ResultResponse();
        try {
            result = saveSearchService.deleteSearch(id);
        } catch (Exception e) {
            result.setCode(500);
            result.setMessage("Something Went Wrong: " + e.getMessage());
            result.setStatus(ResponseStatus.FAILURE);
        }
        return result;
    }
}