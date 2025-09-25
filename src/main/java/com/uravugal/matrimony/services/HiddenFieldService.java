package com.uravugal.matrimony.services;

import com.uravugal.matrimony.models.HiddenFieldEntity;
import com.uravugal.matrimony.repositories.HiddenFieldRepository;
import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ResponseStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
public class HiddenFieldService {

    @Autowired
    private HiddenFieldRepository hiddenFieldRepository;

    public ResultResponse createHideField(String userId, String fieldName) {
        ResultResponse response = new ResultResponse();
        try {
            Long decodedId = Long.parseLong(new String(Base64.getDecoder().decode(userId), StandardCharsets.UTF_8));
            HiddenFieldEntity hiddenField = new HiddenFieldEntity();
            hiddenField.setUserId(decodedId);
            hiddenField.setFieldName(fieldName);

            HiddenFieldEntity saved = hiddenFieldRepository.save(hiddenField);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Field hidden successfully.");
            response.setData(saved);
        } catch (Exception e) {
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("An error occurred while hiding the field: " + e.getMessage());
        }
        return response;
    }


    public ResultResponse unhideField(Long hiddenFieldId) {
        ResultResponse response = new ResultResponse();
        try {
            Optional<HiddenFieldEntity> optionalHiddenField = hiddenFieldRepository.findById(hiddenFieldId);
            if (!optionalHiddenField.isPresent()) {
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("Hidden field not found.");
                return response;
            }

            hiddenFieldRepository.deleteById(hiddenFieldId);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Field unhidden successfully.");
        } catch (Exception e) {
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("An error occurred while unhiding the field: " + e.getMessage());
        }
        return response;
    }

    public ResultResponse getHiddenFieldsByUserId(Long userId) {
        ResultResponse response = new ResultResponse();
        try {
            // Long decodedId = Long.parseLong(new String(Base64.getDecoder().decode(userId), StandardCharsets.UTF_8));
            List<HiddenFieldEntity> hiddenFields = hiddenFieldRepository.findByUserId(userId);
            if (hiddenFields.isEmpty()) {
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("No hidden fields found for user.");
                return response;
            }

            response.setStatus(ResponseStatus.SUCCESS);
            response.setData(hiddenFields);
        } catch (Exception e) {
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("An error occurred while retrieving hidden fields: " + e.getMessage());
        }
        return response;
    }
}
