package com.uravugal.matrimony.services;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.KeyValue;
import com.uravugal.matrimony.repositories.KeyValueRepository;

@Service
public class KeyValueService {
    @Autowired
    private KeyValueRepository keyValueRepository;
    
    public ResultResponse createKeyValue(KeyValue keyValue) {
        ResultResponse response = new ResultResponse();
        try {
            if (keyValueRepository.existsByKey(keyValue.getKey())) {
                response.setCode(HttpStatus.CONFLICT.value());
                response.setMessage("Key already exists");
                response.setStatus(ResponseStatus.FAILURE);
                return response;
            }
            KeyValue savedKeyValue = keyValueRepository.save(keyValue);
            response.setData(savedKeyValue);
            response.setCode(HttpStatus.CREATED.value());
            response.setMessage("Key-Value pair created successfully");
            response.setStatus(ResponseStatus.SUCCESS);
        } catch (Exception e) {
            response.setCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Failed to create key-value pair: " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }
    
    public ResultResponse getKeyValueById(Long id) {
        ResultResponse response = new ResultResponse();
        try {
            Optional<KeyValue> keyValue = keyValueRepository.findById(id);
            if (keyValue.isPresent()) {
                response.setData(keyValue.get());
                response.setCode(HttpStatus.OK.value());
                response.setMessage("Key-Value pair retrieved successfully");
                response.setStatus(ResponseStatus.SUCCESS);
            } else {
                response.setCode(HttpStatus.NOT_FOUND.value());
                response.setMessage("Key-Value pair not found with id: " + id);
                response.setStatus(ResponseStatus.FAILURE);
            }
        } catch (Exception e) {
            response.setCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Failed to retrieve key-value pair: " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }
    
    public ResultResponse getKeyValueByKey(String key) {
        ResultResponse response = new ResultResponse();
        try {
            Optional<KeyValue> keyValue = keyValueRepository.findByKey(key);
            if (keyValue.isPresent()) {
                response.setData(keyValue.get());
                response.setCode(HttpStatus.OK.value());
                response.setMessage("Key-Value pair retrieved successfully");
                response.setStatus(ResponseStatus.SUCCESS);
            } else {
                response.setCode(HttpStatus.NOT_FOUND.value());
                response.setMessage("Key-Value pair not found with key: " + key);
                response.setStatus(ResponseStatus.FAILURE);
            }
        } catch (Exception e) {
            response.setCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Failed to retrieve key-value pair: " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }
    
    public ResultResponse getAllKeyValues() {
        ResultResponse response = new ResultResponse();
        try {
            List<KeyValue> keyValues = keyValueRepository.findAll();
            response.setData(keyValues);
            response.setCode(HttpStatus.OK.value());
            response.setMessage("All key-value pairs retrieved successfully");
            response.setStatus(ResponseStatus.SUCCESS);
        } catch (Exception e) {
            response.setCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Failed to retrieve key-value pairs: " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }
    
    public ResultResponse updateKeyValue(Long id, KeyValue keyValueDetails) {
        ResultResponse response = new ResultResponse();
        try {
            Optional<KeyValue> keyValueOptional = keyValueRepository.findById(id);
            if (keyValueOptional.isPresent()) {
                KeyValue existingKeyValue = keyValueOptional.get();
                
                // Check if the new key already exists for a different record
                if (!existingKeyValue.getKey().equals(keyValueDetails.getKey()) && 
                    keyValueRepository.existsByKey(keyValueDetails.getKey())) {
                    response.setCode(HttpStatus.CONFLICT.value());
                    response.setMessage("Key already exists");
                    response.setStatus(ResponseStatus.FAILURE);
                    return response;
                }
                
                existingKeyValue.setKey(keyValueDetails.getKey());
                existingKeyValue.setValue(keyValueDetails.getValue());
                
                KeyValue updatedKeyValue = keyValueRepository.save(existingKeyValue);
                response.setData(updatedKeyValue);
                response.setCode(HttpStatus.OK.value());
                response.setMessage("Key-Value pair updated successfully");
                response.setStatus(ResponseStatus.SUCCESS);
            } else {
                response.setCode(HttpStatus.NOT_FOUND.value());
                response.setMessage("Key-Value pair not found with id: " + id);
                response.setStatus(ResponseStatus.FAILURE);
            }
        } catch (Exception e) {
            response.setCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Failed to update key-value pair: " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }
    
    public ResultResponse deleteKeyValue(Long id) {
        ResultResponse response = new ResultResponse();
        try {
            if (keyValueRepository.existsById(id)) {
                keyValueRepository.deleteById(id);
                response.setCode(HttpStatus.OK.value());
                response.setMessage("Key-Value pair deleted successfully");
                response.setStatus(ResponseStatus.SUCCESS);
            } else {
                response.setCode(HttpStatus.NOT_FOUND.value());
                response.setMessage("Key-Value pair not found with id: " + id);
                response.setStatus(ResponseStatus.FAILURE);
            }
        } catch (Exception e) {
            response.setCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setMessage("Failed to delete key-value pair: " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }
}
