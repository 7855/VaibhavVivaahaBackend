package com.uravugal.matrimony.services;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.uravugal.matrimony.enums.S3BucketMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
public class S3FileUploadService {

    @Autowired
    private AmazonS3 amazonS3;

    @Autowired
    private S3BucketMapping s3BucketMap;

    private static final Logger logger = LoggerFactory.getLogger(S3FileUploadService.class);

    private String uploadFile(File file, String folderPath, String bucketName, boolean enablePublicReadAccess) {
        try {
            logger.info("Bucket name: " + bucketName);
            String filePath = file.getName();
            if (folderPath != null) {
                filePath = folderPath + "/" + file.getName();
            }
            System.out.println("----->" + folderPath + bucketName);
            PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, filePath, file);
            // if (enablePublicReadAccess) {
            //     putObjectRequest.withCannedAcl(CannedAccessControlList.PublicRead);
            // }

            this.amazonS3.putObject(putObjectRequest);
            String s3FileUrl = String.valueOf(amazonS3.getUrl(bucketName, filePath));
            return s3FileUrl;
        } catch (AmazonServiceException ex) {
            logger.error("error [" + ex.getMessage() + "] occurred while uploading [" + file.getName() + "] ");
            throw ex;
        }
    }

    public String uploadNews(File image, String folderPath) {
        String bucketName = getS3BucketDetails(S3BucketMap.GALLERY);
        return uploadFile(image, folderPath, bucketName, true);
    }

    public String uploadTeam(File document, String folderPath) {
        String bucketName = getS3BucketDetails(S3BucketMap.DOCUMENT);
        return uploadFile(document, folderPath, bucketName, true);
    }

    public String uploadGalleryImage(File image, String folderPath) {
        System.out.println("----->" + folderPath);
        String bucketName = getS3BucketDetails(S3BucketMap.GALLERY);
        return uploadFile(image, folderPath, bucketName, true);
    }

    public String uploadSponsorImage(File image, String folderPath) {
        String bucketName = getS3BucketDetails(S3BucketMap.SPONSOR);
        return uploadFile(image, folderPath, bucketName, true);
    }

    public String uploadVideos(File video, String folderPath) {
        String bucketName = getS3BucketDetails(S3BucketMap.VIDEO);
        return uploadFile(video, folderPath, bucketName, true);
    }

    private String getS3BucketDetails(S3BucketMap key) {
        return s3BucketMap.getValue(key);
    }
}
