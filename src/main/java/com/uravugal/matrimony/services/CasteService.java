package com.uravugal.matrimony.services;

import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ActiveStatus;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.CasteEntity;
import com.uravugal.matrimony.repositories.CasteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

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

    public ResultResponse createCaste(CasteEntity caste) {
        ResultResponse resp = new ResultResponse();
        try {
            caste.setIsActive(ActiveStatus.Y);
            casteRepository.save(caste);
            resp.setCode(200);
            resp.setMessage("Caste created successfully");
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setData(caste);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Error creating caste: " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    public ResultResponse updateCaste(Long id, CasteEntity updated) {
        ResultResponse resp = new ResultResponse();
        try {
            Optional<CasteEntity> opt = casteRepository.findById(id);
            if (opt.isEmpty()) {
                resp.setCode(404);
                resp.setMessage("Caste not found");
                resp.setStatus(ResponseStatus.FAILURE);
                return resp;
            }
            CasteEntity existing = opt.get();
            existing.setCasteName(updated.getCasteName());
            casteRepository.save(existing);
            resp.setCode(200);
            resp.setMessage("Caste updated successfully");
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setData(existing);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Error updating caste: " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }
}
