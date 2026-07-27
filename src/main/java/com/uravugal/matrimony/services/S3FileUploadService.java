package com.uravugal.matrimony.services;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.uravugal.matrimony.enums.S3BucketMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.springframework.beans.factory.annotation.Value;
import java.io.File;
import java.io.FileInputStream;

@Component
public class S3FileUploadService {

    @Autowired
    private AmazonS3 amazonS3;

    @Autowired
    private S3BucketMapping s3BucketMap;

    @Value("${aws.s3.public.url:}")
    private String publicUrl;

    private static final Logger logger = LoggerFactory.getLogger(S3FileUploadService.class);

    private String uploadFile(File file, String folderPath, String bucketName, boolean enablePublicReadAccess) {

        try {
            logger.info("Bucket name: " + bucketName);

            String filePath = file.getName();
            if (folderPath != null) {
                filePath = folderPath + "/" + file.getName();
            }

            System.out.println("Uploading filePath => " + filePath);
            System.out.println("File size => " + file.length());

            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.length());

            FileInputStream inputStream = new FileInputStream(file);

            PutObjectRequest request = new PutObjectRequest(
                    bucketName,
                    filePath,
                    inputStream,
                    metadata);

            if (enablePublicReadAccess) {
                request.withCannedAcl(CannedAccessControlList.PublicRead);
            }

            amazonS3.putObject(request);

            String s3FileUrl;
            if (publicUrl != null && !publicUrl.trim().isEmpty()) {
                String cleanPublicUrl = publicUrl.trim();
                if (cleanPublicUrl.endsWith("/")) {
                    cleanPublicUrl = cleanPublicUrl.substring(0, cleanPublicUrl.length() - 1);
                }
                s3FileUrl = cleanPublicUrl + "/" + filePath;
            } else {
                s3FileUrl = amazonS3.getUrl(bucketName, filePath).toString();
            }

            inputStream.close();
            return s3FileUrl;

        } catch (Exception ex) {
            logger.error("S3 Upload Error: " + ex.getMessage(), ex);
            throw new RuntimeException(ex);
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
