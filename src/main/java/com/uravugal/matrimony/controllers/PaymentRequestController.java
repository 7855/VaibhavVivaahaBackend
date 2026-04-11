package com.uravugal.matrimony.controllers;

import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.services.PaymentRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.google.firebase.database.annotations.Nullable;
import java.math.BigDecimal;
import java.util.Base64;

@RestController
@RequestMapping("/paymentrequest")
@CrossOrigin(origins = "*")
public class PaymentRequestController {

    @Autowired
    private PaymentRequestService paymentRequestService;

    @PostMapping(path = "/create", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public ResultResponse createPaymentRequest(
            @RequestPart("userId") String userId,
            @RequestPart("planId") String planId,
            @RequestPart("amount") String amount,
            @RequestPart("utrNumber") String utrNumber,
            @RequestPart("file") @Nullable MultipartFile file) {

        ResultResponse response = new ResultResponse();
        try {
            System.out.println("userId==>" + userId);
            System.out.println("planId==>" + planId);
            System.out.println("amount==>" + amount);
            System.out.println("utrNumber==>" + utrNumber);

            String decodedId = new String(Base64.getDecoder().decode(userId));
            Long parsedUserId = Long.parseLong(decodedId);
            Long parsedPlanId = Long.parseLong(planId);
            BigDecimal parsedAmount = new BigDecimal(amount);
            response = paymentRequestService.createPaymentRequest(parsedUserId, parsedPlanId, parsedAmount, utrNumber,
                    file);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Error creating payment request: " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    @GetMapping("/getPaymentRequestsByUser/{encodedUserId}")
    public ResultResponse getPaymentRequestsByUser(@PathVariable String encodedUserId) {
        ResultResponse response = new ResultResponse();
        try {
            String decodedId = new String(Base64.getDecoder().decode(encodedUserId));
            Long userId = Long.parseLong(decodedId);
            response = paymentRequestService.getPaymentRequestsByUser(userId);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }
}
