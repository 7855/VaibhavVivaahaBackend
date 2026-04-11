package com.uravugal.matrimony.services;

import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ActiveStatus;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.GalleryEntity;
import com.uravugal.matrimony.models.UserDetailEntity;
import com.uravugal.matrimony.models.UserEntity;
import com.uravugal.matrimony.repositories.GalleryRepository;
import com.uravugal.matrimony.repositories.UserDetailRepository;
import com.uravugal.matrimony.repositories.UserRepository;

@Service
public class GalleryService {
    
    @Autowired
    private GalleryRepository galleryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserDetailRepository userDetailRepository;

    private static final String AWS_BASE_PATH = "userGallery/";
    private static final String AWS_BASE_PATH_HO = "userHoroscope/";
    private static final int MAX_GALLERY_PHOTOS = 3;

    
    @Autowired
    private S3FileUploadService s3UploadService;

    public ResultResponse getAllImagesByUserId(String encodedId) {
        ResultResponse response = new ResultResponse();
        try {
            String decodedId = new String(Base64.getDecoder().decode(encodedId));
            Long userId = Long.parseLong(decodedId);
            // System.out.println("gyegyeeeeeeeeeg "+userId);
            List<GalleryEntity> images = galleryRepository.findByUserIdAndIsActive(userId, ActiveStatus.Y);
            
            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);     
            response.setMessage("Images fetched successfully.");
            response.setData(images);
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error fetching images: " + e.getMessage());
        }
        return response;
    }

    public ResultResponse changeImageActiveStatus(Long galleryId) {
        ResultResponse response = new ResultResponse();
        try {
            GalleryEntity image = galleryRepository.findByGalleryIdAndIsActive(galleryId, ActiveStatus.Y);
            if (image != null) {
                image.setIsActive(ActiveStatus.N);
                galleryRepository.save(image);
                
                response.setCode(200);
                response.setStatus(ResponseStatus.SUCCESS);
                response.setMessage("Image status updated successfully.");
            } else {
                response.setCode(404);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("Image not found or already inactive.");
            }
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error updating image status: " + e.getMessage());
        }
        return response;
    }

    public ResultResponse uploadGalleryImage(String userId, MultipartFile file) {
        ResultResponse response = new ResultResponse();
        try {
            // Find the user            UserEntity user = userRepository.findById(id).orElse(null);
            Long id = Long.parseLong(userId);
            UserEntity user = userRepository.findById(id).orElse(null);
            
            if (user == null) {
                response.setCode(404);
                response.setMessage("User not found");
                response.setStatus(ResponseStatus.FAILURE);
                return response;
            }
            
            // Check gallery photo limit
            List<GalleryEntity> existingImages = galleryRepository.findByUserIdAndIsActive(id, ActiveStatus.Y);
            if (existingImages.size() >= MAX_GALLERY_PHOTOS) {
                response.setCode(400);
                response.setMessage("Maximum " + MAX_GALLERY_PHOTOS + " gallery photos allowed. Please delete an existing photo first.");
                response.setStatus(ResponseStatus.FAILURE);
                return response;
            }

            if (file != null && !file.isEmpty()) {
                try {
                    // Generate a unique file name
                    String fileName = "profile_" + System.currentTimeMillis() + "_"
                            + UUID.randomUUID().toString().substring(0, 6)
                            + "." + getFileExtension(file.getOriginalFilename());

                    // Create a temporary file with the EXACT target name so S3 uses it
                    File tempDir = new File(System.getProperty("java.io.tmpdir"));
                    File tempFile = new File(tempDir, fileName);
                    file.transferTo(tempFile);

                    // Upload to S3 — uses fileName as the actual S3 key
                    String fileUrl = s3UploadService.uploadGalleryImage(tempFile, AWS_BASE_PATH + "user_" + userId);
                    System.out.println("File URL: ------>" + fileUrl);

                    // Delete temp file after upload
                    tempFile.delete();

                    // Update user's Gallery Record
                    GalleryEntity galleryEntity = new GalleryEntity();
                    galleryEntity.setUserId(id);
                    galleryEntity.setIsActive(ActiveStatus.Y);
                    galleryEntity.setUserImage(fileUrl);
                    galleryRepository.save(galleryEntity);

                    response.setCode(200);
                    response.setMessage("Profile image updated successfully");
                    response.setStatus(ResponseStatus.SUCCESS);
                    response.setData(fileUrl);
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
        } catch (NumberFormatException e) {
            response.setCode(400);
            response.setMessage("Invalid user ID format");
            response.setStatus(ResponseStatus.FAILURE);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Error updating profile image: " + e.getMessage());
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

    public ResultResponse uploadHoroscopeImage(String userId, MultipartFile file) {
        ResultResponse response = new ResultResponse();
        try {
            // Find the user            UserEntity user = userRepository.findById(id).orElse(null);
            Long id = Long.parseLong(userId);
            UserEntity user = userRepository.findById(id).orElse(null);
            
            if (user == null) {
                response.setCode(404);
                response.setMessage("User not found");
                response.setStatus(ResponseStatus.FAILURE);
                return response;
            }
            
            if (file != null && !file.isEmpty()) {
                try {
                    // Generate a unique file name
                    String fileName = "horoscope_" + System.currentTimeMillis() + "_"
                            + UUID.randomUUID().toString().substring(0, 6)
                            + "." + getFileExtension(file.getOriginalFilename());

                    // Create a temporary file with the EXACT target name so S3 uses it
                    File tempDir = new File(System.getProperty("java.io.tmpdir"));
                    File tempFile = new File(tempDir, fileName);
                    file.transferTo(tempFile);

                    // Upload to S3 — uses fileName as the actual S3 key
                    String fileUrl = s3UploadService.uploadGalleryImage(tempFile, AWS_BASE_PATH_HO + "user_" + userId);
                    System.out.println("File URL: ------>" + fileUrl);

                    // Delete temp file after upload
                    tempFile.delete();

                    // Update user's Horoscope Record
                     UserDetailEntity userDetail = userDetailRepository.findByUserId(id);
                     Boolean isCreate = userDetail.getHoroscope() == null;
                     if(userDetail != null){
                        userDetail.setHoroscope(fileUrl);
                        userDetailRepository.save(userDetail);
                     }
                    
                    response.setCode(isCreate ? 200 : 201);
                    response.setMessage(isCreate ? "Horoscope image created successfully":"Horoscope image updated successfully");
                    response.setStatus(ResponseStatus.SUCCESS);
                    response.setData(fileUrl);

                    System.out.println("Horoscope image uploaded successfully");
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
        } catch (NumberFormatException e) {
            response.setCode(400);
            response.setMessage("Invalid user ID format");
            response.setStatus(ResponseStatus.FAILURE);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Error updating profile image: " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    public ResultResponse deleteHoroscope(String encodedUserId) {
        ResultResponse response = new ResultResponse();
        try {
            Long userId = Long.parseLong(new String(java.util.Base64.getDecoder().decode(encodedUserId)));
            UserDetailEntity userDetail = userDetailRepository.findByUserId(userId);
            if (userDetail == null) {
                response.setCode(404);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("User details not found");
                return response;
            }
            if (userDetail.getHoroscope() == null || userDetail.getHoroscope().isBlank()) {
                response.setCode(404);
                response.setStatus(ResponseStatus.FAILURE);
                response.setMessage("No horoscope image to delete");
                return response;
            }
            userDetail.setHoroscope(null);
            userDetailRepository.save(userDetail);
            response.setCode(200);
            response.setStatus(ResponseStatus.SUCCESS);
            response.setMessage("Horoscope image deleted successfully");
        } catch (Exception e) {
            response.setCode(500);
            response.setStatus(ResponseStatus.FAILURE);
            response.setMessage("Error deleting horoscope: " + e.getMessage());
        }
        return response;
    }
}
