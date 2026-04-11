package com.uravugal.matrimony.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ActiveStatus;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.SavedSearchFilterEntity;
import com.uravugal.matrimony.models.SavedSearchesEntity;
import com.uravugal.matrimony.repositories.SaveSearchRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
public class SaveSearchService {
    @Autowired
    private SaveSearchRepository saveSearchRepository;

    public ResultResponse getUserSavedSearches(Long userId) {
        ResultResponse response = new ResultResponse();
        try {
            List<SavedSearchesEntity> savedSearch = saveSearchRepository.findByUserIdAndIsActive(userId,"Y");
            if (savedSearch.size() > 0) {
                response.setCode(200);
                response.setData(savedSearch);
                response.setStatus(ResponseStatus.SUCCESS);
                response.setMessage("Saved Search Data Fetched Successfully.");
            } else {
                response.setCode(404);
                response.setStatus(ResponseStatus.SUCCESS);
                response.setMessage("No Saved Search Record Found For Given User.");
            }
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

@Transactional
public ResultResponse createOrUpdateSavedSearch(
        SavedSearchesEntity savedSearch,
        Map<String, Object> filterRequest
) {
    ResultResponse response = new ResultResponse();

    try {
        if (savedSearch == null) {
            response.setCode(400);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Saved search data cannot be null");
            return response;
        }

        List<SavedSearchFilterEntity> filters =
                buildFiltersFromRequest(filterRequest, savedSearch);

        savedSearch.setFilters(filters);

        filters.forEach(f -> f.setSavedSearch(savedSearch));

        SavedSearchesEntity savedEntity = saveSearchRepository.save(savedSearch);

        response.setCode(200);
        response.setStatus(ResponseStatus.SUCCESS);
        response.setMessage("Search saved successfully");
        response.setData(savedEntity);

    } catch (Exception e) {
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        response.setCode(500);
        response.setStatus(ResponseStatus.FAILURE);
        response.setMessage("Failed to save search. " + e.getMessage());
    }

    return response;
}


private List<SavedSearchFilterEntity> buildFiltersFromRequest(
        Map<String, Object> filterRequest,
        SavedSearchesEntity savedSearch
) {
    List<SavedSearchFilterEntity> filters = new ArrayList<>();

    if (filterRequest == null) {
        return filters;
    }

    filterRequest.forEach((key, value) -> {
        if (value == null) return;

        SavedSearchFilterEntity filter = new SavedSearchFilterEntity();
        filter.setSavedSearch(savedSearch);
        filter.setFilterKey(key);

        if (value instanceof List) {
            // Convert list to comma-separated string
            String csv = ((List<?>) value).stream()
                .map(Object::toString)
                .collect(Collectors.joining(","));
            filter.setFilterValue(csv);
        } else {
            // For strings (including ranges like "33 - 37")
            filter.setFilterValue(value.toString());
        }

        filters.add(filter);
    });

    return filters;
}


    @Transactional
    public ResultResponse deleteSearch(Long id) {
        ResultResponse response = new ResultResponse();
        try {
            // Find the saved search by id and userId
            SavedSearchesEntity search = saveSearchRepository.findById(id)
                    .orElseThrow(() -> new NoSuchElementException(
                            "Saved search not found with id: " + id));

            // Soft delete by setting isActive to 'N'
            search.setIsActive("N");
            SavedSearchesEntity updatedSearch = saveSearchRepository.save(search);

            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Search deleted successfully");
            response.setData(updatedSearch);
        } catch (NoSuchElementException e) {
            response.setCode(404);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage(e.getMessage());
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Failed to delete search: " + e.getMessage());
        }
        return response;
    }
}
