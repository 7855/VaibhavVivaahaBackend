package com.uravugal.matrimony.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.services.PaymentService;

@RestController
@RequestMapping("/payments")
@CrossOrigin(origins = "*")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @PostMapping("/createOrder/{amount}")
    public ResultResponse createOrder(
            @PathVariable Long amount) {
        ResultResponse response = new ResultResponse();
        
        try {
            if (amount == null || amount <= 0) {
                response.setStatus(ResponseStatus.FAILURE);
                response.setCode(400);
                response.setMessage("Amount must be a positive number");
                return response;
            }
            
            response = paymentService.createOrder(amount);
            
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(ResponseStatus.FAILURE);
            response.setCode(500);
            response.setMessage("Error creating order: " + e.getMessage());
        }
        
        return response;
    }
}
