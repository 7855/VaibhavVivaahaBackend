package com.uravugal.matrimony.services;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ResponseStatus;

@Service
public class PaymentService {

    @Value("${razorpay.key.id}")
    private String razorpayKeyId;

    @Value("${razorpay.key.secret}")
    private String razorpayKeySecret;

    public ResultResponse createOrder(Long amount) {
        ResultResponse response = new ResultResponse();
        
        try {
            RazorpayClient razorpay = new RazorpayClient(razorpayKeyId, razorpayKeySecret);
            
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amount); // amount in the smallest currency unit
            orderRequest.put("currency", "INR");
            String receiptId = "order_rcptid_" + System.currentTimeMillis(); 
            orderRequest.put("receipt", receiptId);
            
            Order order = razorpay.orders.create(orderRequest);
            
            // Convert the order to a Map to include all fields in the response
            JSONObject orderJson = new JSONObject(order.toString());
            
            response.setStatus(ResponseStatus.SUCCESS);
            response.setCode(200);
            response.setMessage("Order created successfully");
            response.setData(orderJson.toMap());
            
        } catch (RazorpayException e) {
            e.printStackTrace();
            response.setStatus(ResponseStatus.FAILURE);
            response.setCode(500);
            response.setMessage("Error creating order: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(ResponseStatus.FAILURE);
            response.setCode(500);
            response.setMessage("Unexpected error occurred");
        }
        
        return response;
    }
}
