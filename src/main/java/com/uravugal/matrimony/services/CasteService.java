package com.uravugal.matrimony.services;

import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ActiveStatus;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.CasteEntity;
import com.uravugal.matrimony.repositories.CasteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CasteService {
    
    @Autowired
    private CasteRepository casteRepository;

    public ResultResponse getAllByActiveStatus() {
        ResultResponse resp = new ResultResponse();
        try {
            List<CasteEntity> castes = casteRepository.findByIsActive(ActiveStatus.Y);
            resp.setCode(200);
            resp.setMessage("Castes fetched successfully");
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setData(castes);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Error fetching castes: " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }
}
