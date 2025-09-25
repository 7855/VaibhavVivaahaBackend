package com.uravugal.matrimony.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.uravugal.matrimony.enums.S3BucketMap;

import java.util.HashMap;

@Component
public class S3BucketMapping {

    HashMap<S3BucketMap, String> bucketMapping;

    @Value("${aws.s3.bucket.documents}")
    private String s3DocumentBucket;
    @Value("${aws.s3.bucket.videos}")
    private String s3VideoBucket;
    @Value("${aws.s3.bucket.gallery}")
    private String s3GalleryBucket;
    @Value("${aws.s3.bucket.sponsor}")
    private String s3SponsorBucket;

    public String getValue(S3BucketMap key){
        bucketMapping = new HashMap<>();
        bucketMapping.put(S3BucketMap.DOCUMENT, s3DocumentBucket);
        bucketMapping.put(S3BucketMap.VIDEO, s3VideoBucket);
        bucketMapping.put(S3BucketMap.GALLERY, s3GalleryBucket);
        bucketMapping.put(S3BucketMap.SPONSOR, s3SponsorBucket);
        if(bucketMapping.containsKey(key)){
            return bucketMapping.get(key);
        }
        return bucketMapping.get(S3BucketMap.DOCUMENT);
    }
}
