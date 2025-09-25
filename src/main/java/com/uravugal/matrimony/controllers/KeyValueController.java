package com.uravugal.matrimony.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.KeyValue;
import com.uravugal.matrimony.services.KeyValueService;

@RestController
@RequestMapping("/keyValue")
public class KeyValueController {
    
    @Autowired
    private KeyValueService keyValueService;
    
    /**
     * Create a new key-value pair
     * @param keyValue The key-value pair to create
     * @return The created key-value pair
     */
    @PostMapping
    public ResultResponse createKeyValue(@RequestBody KeyValue keyValue) {
        try {
            return keyValueService.createKeyValue(keyValue);
        } catch (Exception e) {
            ResultResponse errorResponse = new ResultResponse();
            errorResponse.setCode(500);
            errorResponse.setStatus(ResponseStatus.FAILURE);
            errorResponse.setMessage("Error creating key-value pair: " + e.getMessage());
            return errorResponse;
        }
    }
    
    /**
     * Get a key-value pair by ID
     * @param id The ID of the key-value pair to retrieve
     * @return The requested key-value pair
     */
    @GetMapping("/getKeyValueById/{id}")
    public ResultResponse getKeyValueById(@PathVariable Long id) {
        try {
            return keyValueService.getKeyValueById(id);
        } catch (Exception e) {
            ResultResponse errorResponse = new ResultResponse();
            errorResponse.setCode(500);
            errorResponse.setStatus(ResponseStatus.FAILURE);
            errorResponse.setMessage("Error retrieving key-value pair: " + e.getMessage());
            return errorResponse;
        }
    }
    
    /**
     * Get a key-value pair by key
     * @param key The key of the key-value pair to retrieve
     * @return The requested key-value pair
     */
    @GetMapping("/getKeyValueByKey/{key}")
    public ResultResponse getKeyValueByKey(@PathVariable String key) {
        try {
            return keyValueService.getKeyValueByKey(key);
        } catch (Exception e) {
            ResultResponse errorResponse = new ResultResponse();
            errorResponse.setCode(500);
            errorResponse.setStatus(ResponseStatus.FAILURE);
            errorResponse.setMessage("Error retrieving key-value pair by key: " + e.getMessage());
            return errorResponse;
        }
    }
    
    /**
     * Get all key-value pairs
     * @return A list of all key-value pairs
     */
    @GetMapping("/getAllKeyValues")
    public ResultResponse getAllKeyValues() {
        try {
            return keyValueService.getAllKeyValues();
        } catch (Exception e) {
            ResultResponse errorResponse = new ResultResponse();
            errorResponse.setCode(500);
            errorResponse.setStatus(ResponseStatus.FAILURE);
            errorResponse.setMessage("Error retrieving all key-value pairs: " + e.getMessage());
            return errorResponse;
        }
    }
    
    /**
     * Update an existing key-value pair
     * @param id The ID of the key-value pair to update
     * @param keyValueDetails The updated key-value pair data
     * @return The updated key-value pair
     */
    @PutMapping("/updateKeyValue/{id}")
    public ResultResponse updateKeyValue(
            @PathVariable Long id, 
            @RequestBody KeyValue keyValueDetails) {
        try {
            return keyValueService.updateKeyValue(id, keyValueDetails);
        } catch (Exception e) {
            ResultResponse errorResponse = new ResultResponse();
            errorResponse.setCode(500);
            errorResponse.setStatus(ResponseStatus.FAILURE);
            errorResponse.setMessage("Error updating key-value pair: " + e.getMessage());
            return errorResponse;
        }
    }
    
    /**
     * Delete a key-value pair
     * @param id The ID of the key-value pair to delete
     * @return A success message
     */
    @DeleteMapping("/deleteKeyValue/{id}")
    public ResultResponse deleteKeyValue(@PathVariable Long id) {
        try {
            return keyValueService.deleteKeyValue(id);
        } catch (Exception e) {
            ResultResponse errorResponse = new ResultResponse();
            errorResponse.setCode(500);
            errorResponse.setStatus(ResponseStatus.FAILURE);
            errorResponse.setMessage("Error deleting key-value pair: " + e.getMessage());
            return errorResponse;      
        }
    }
}
