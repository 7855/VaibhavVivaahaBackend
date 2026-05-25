package com.uravugal.matrimony.services;

import com.uravugal.matrimony.dtos.PaginatedResultResponse;
import com.uravugal.matrimony.dtos.PaginationData;
import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.CallbackRequest;
import com.uravugal.matrimony.repositories.CallbackRequestRepository;

import java.util.Base64;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class CallbackRequestService {

    private static final Set<String> ALLOWED_STATUSES =
            Set.of("NEW", "CONTACTED", "CONVERTED", "CLOSED");

    @Autowired
    private CallbackRequestRepository callbackRequestRepository;

    public ResultResponse createRequest(String encodedUserId, String name, String mobile,
                                        String email, String planInterested,
                                        String note, String bestTimeToCall) {
        ResultResponse resp = new ResultResponse();
        try {
            if (name == null || name.isBlank()) {
                resp.setCode(400);
                resp.setStatus(ResponseStatus.FAILURE);
                resp.setMessage("Name is required");
                return resp;
            }
            if (mobile == null || !mobile.matches("^\\+?\\d{10,15}$")) {
                resp.setCode(400);
                resp.setStatus(ResponseStatus.FAILURE);
                resp.setMessage("Valid mobile number is required");
                return resp;
            }

            Long userId = null;
            if (encodedUserId != null && !encodedUserId.isBlank() && !"guest".equalsIgnoreCase(encodedUserId)) {
                try {
                    userId = Long.parseLong(new String(Base64.getDecoder().decode(encodedUserId)));
                } catch (Exception ignored) {}
            }

            // Dedup — block duplicate NEW requests from same user for same plan
            if (userId != null && planInterested != null) {
                CallbackRequest existing = callbackRequestRepository
                        .findFirstByUserIdAndPlanInterestedAndStatus(userId, planInterested, "NEW");
                if (existing != null) {
                    resp.setCode(409);
                    resp.setStatus(ResponseStatus.FAILURE);
                    resp.setMessage("A callback request for this plan is already in progress. Our team will reach you shortly.");
                    resp.setData(existing);
                    return resp;
                }
            }

            CallbackRequest cr = new CallbackRequest();
            cr.setUserId(userId);
            cr.setName(name.trim());
            cr.setMobile(mobile.trim());
            cr.setEmail(email != null ? email.trim() : null);
            cr.setPlanInterested(planInterested);
            cr.setNote(note);
            cr.setBestTimeToCall(bestTimeToCall);
            cr.setStatus("NEW");
            CallbackRequest saved = callbackRequestRepository.save(cr);

            resp.setCode(200);
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setMessage("Callback request received. Our team will reach you within 24 hours.");
            resp.setData(saved);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setStatus(ResponseStatus.FAILURE);
            resp.setMessage("Error creating callback request: " + e.getMessage());
        }
        return resp;
    }

    public PaginatedResultResponse getMyRequests(String encodedUserId, Integer page, Integer size) {
        PaginatedResultResponse resp = new PaginatedResultResponse();
        try {
            Long userId = Long.parseLong(new String(Base64.getDecoder().decode(encodedUserId)));
            Page<CallbackRequest> p = callbackRequestRepository.findByUserIdOrderByIdDesc(
                    userId, PageRequest.of(page != null ? page : 0, size != null ? size : 10));
            resp.setCode(200);
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setMessage("My callback requests fetched");
            resp.setData(p.getContent());
            PaginationData pg = new PaginationData();
            pg.setTotalPages(p.getTotalPages());
            pg.setTotalElements(p.getTotalElements());
            pg.setCurrentPage(p.getNumber());
            pg.setPageSize(p.getSize());
            resp.setPaginationData(pg);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setStatus(ResponseStatus.FAILURE);
            resp.setMessage("Error: " + e.getMessage());
        }
        return resp;
    }

    public PaginatedResultResponse adminListRequests(String status, Integer page, Integer size) {
        PaginatedResultResponse resp = new PaginatedResultResponse();
        try {
            PageRequest pr = PageRequest.of(page != null ? page : 0, size != null ? size : 20);
            Page<CallbackRequest> p;
            if (status != null && !status.isBlank()) {
                p = callbackRequestRepository.findByStatusOrderByIdDesc(status, pr);
            } else {
                p = callbackRequestRepository.findAllByOrderByIdDesc(pr);
            }
            resp.setCode(200);
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setMessage("Callback requests fetched");
            resp.setData(p.getContent());
            PaginationData pg = new PaginationData();
            pg.setTotalPages(p.getTotalPages());
            pg.setTotalElements(p.getTotalElements());
            pg.setCurrentPage(p.getNumber());
            pg.setPageSize(p.getSize());
            resp.setPaginationData(pg);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setStatus(ResponseStatus.FAILURE);
            resp.setMessage("Error: " + e.getMessage());
        }
        return resp;
    }

    public ResultResponse adminUpdateStatus(Long id, String newStatus, Long adminId) {
        ResultResponse resp = new ResultResponse();
        try {
            if (newStatus == null || !ALLOWED_STATUSES.contains(newStatus)) {
                resp.setCode(400);
                resp.setStatus(ResponseStatus.FAILURE);
                resp.setMessage("Invalid status. Allowed: " + ALLOWED_STATUSES);
                return resp;
            }
            CallbackRequest cr = callbackRequestRepository.findById(id).orElse(null);
            if (cr == null) {
                resp.setCode(404);
                resp.setStatus(ResponseStatus.FAILURE);
                resp.setMessage("Callback request not found");
                return resp;
            }
            cr.setStatus(newStatus);
            if (adminId != null) cr.setAssignedAdminId(adminId);
            callbackRequestRepository.save(cr);
            resp.setCode(200);
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setMessage("Status updated");
            resp.setData(cr);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setStatus(ResponseStatus.FAILURE);
            resp.setMessage("Error: " + e.getMessage());
        }
        return resp;
    }
}
