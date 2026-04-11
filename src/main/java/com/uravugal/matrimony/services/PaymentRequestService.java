package com.uravugal.matrimony.services;

import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.PaymentRequestStatus;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.PaymentRequestEntity;
import com.uravugal.matrimony.repositories.PaymentRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.Optional;
import com.uravugal.matrimony.dtos.PaymentRequestResponseDTO;

@Service
public class PaymentRequestService {

    @Autowired
    private PaymentRequestRepository paymentRequestRepository;

    @Autowired
    private S3FileUploadService s3UploadService;

    private static final String AWS_BASE_PATH = "paymentRequests/";

    public ResultResponse createPaymentRequest(Long userId, Long planId, BigDecimal amount, String utrNumber,
            MultipartFile file) {
        ResultResponse response = new ResultResponse();

        if (file != null && !file.isEmpty()) {
            try {
                PaymentRequestEntity paymentRequest = new PaymentRequestEntity();
                paymentRequest.setUserId(userId);
                paymentRequest.setPlanId(planId);
                paymentRequest.setAmount(amount);
                paymentRequest.setUtrNumber(utrNumber);
                paymentRequest.setStatus(PaymentRequestStatus.PENDING);
                // Save initially to generate the ID needed for the S3 path
                paymentRequest = paymentRequestRepository.save(paymentRequest);

                String fileName = "payment_" + System.currentTimeMillis() + "_"
                        + UUID.randomUUID().toString().substring(0, 6)
                        + "." + getFileExtension(file.getOriginalFilename());

                File tempFile = File.createTempFile("temp-", fileName);
                file.transferTo(tempFile);

                String fileUrl = s3UploadService.uploadSponsorImage(tempFile,
                        AWS_BASE_PATH + "req_" + paymentRequest.getId());
                System.out.println("fileUrl==>" + fileUrl);

                int startIndex = fileUrl.indexOf("https://");
                if (startIndex != -1) {
                    String domain = fileUrl.substring(0, fileUrl.indexOf("/", 8));
                    fileUrl = domain + "/" + AWS_BASE_PATH + "req_" + paymentRequest.getId() + "/" + fileName;
                }

                paymentRequest.setScreenshotUrl(fileUrl);
                paymentRequestRepository.save(paymentRequest);

                tempFile.delete();

                response.setCode(200);
                response.setMessage("Payment request created and screenshot uploaded successfully");
                response.setStatus(ResponseStatus.SUCCESS);
                response.setData(paymentRequest);
            } catch (IOException e) {
                response.setCode(500);
                response.setMessage("Error processing image: " + e.getMessage());
                response.setStatus(ResponseStatus.FAILURE);
            }
        } else {
            response.setCode(400);
            response.setMessage("No file provided");
            response.setStatus(ResponseStatus.FAILURE);
        }

        return response;
    }

    public ResultResponse getPaymentRequestsByUser(Long userId) {
        ResultResponse response = new ResultResponse();
        Optional<PaymentRequestEntity> activeRequest = paymentRequestRepository
                .findTopByUserIdOrderByCreatedAtDesc(userId);

        if (activeRequest.isPresent()) {
            PaymentRequestEntity entity = activeRequest.get();
            PaymentRequestResponseDTO dto = new PaymentRequestResponseDTO();
            dto.setId(entity.getId());
            dto.setPlanId(entity.getPlanId());
            dto.setUtrNumber(entity.getUtrNumber());
            dto.setScreenshotUrl(entity.getScreenshotUrl());
            dto.setStatus(entity.getStatus());
            dto.setCreatedAt(entity.getCreatedAt());
            dto.setVerifiedAt(entity.getVerifiedAt());
            dto.setVerifiedBy(entity.getVerifiedBy());

            response.setCode(200);
            response.setMessage("Latest payment request fetched successfully");
            response.setStatus(ResponseStatus.SUCCESS);
            response.setData(dto);
        } else {
            response.setCode(404);
            response.setMessage("No payment request found for user");
            response.setStatus(ResponseStatus.FAILURE);
        }

        return response;
    }

    private String getFileExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int lastDot = fileName.lastIndexOf('.');
        return lastDot == -1 ? "" : fileName.substring(lastDot + 1);
    }
}
