package com.uravugal.matrimony.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.google.firebase.database.annotations.Nullable;
import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.services.GalleryService;

@RestController
@RequestMapping("/gallery")
public class GalleryController {
    
    @Autowired
    private GalleryService galleryService;

    @GetMapping("/getAllImagesByUserId/{encodedId}")
    public ResultResponse getAllImagesByUserId(@PathVariable String encodedId) {
        ResultResponse response = new ResultResponse();
        try {
            
            response = galleryService.getAllImagesByUserId(encodedId);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    @PutMapping("/changeImageActiveStatus/{galleryId}")
    public ResultResponse changeImageActiveStatus(@PathVariable Long galleryId) {
        ResultResponse response = new ResultResponse();
        try {
            response = galleryService.changeImageActiveStatus(galleryId);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }

    @PostMapping(path ="/uploadGalleryImage", consumes={MediaType.MULTIPART_FORM_DATA_VALUE})
	public ResultResponse uploadGalleryImage(@RequestPart("userId") String userId,@RequestPart("file") @Nullable MultipartFile file) {
	    ResultResponse result = new ResultResponse();
	    try {
	        result = galleryService.uploadGalleryImage(userId, file);
	    } catch (Exception e) {
	        result.setCode(500);
	        result.setMessage(e.getMessage());
	        result.setStatus(ResponseStatus.FAILURE);
	    }
	    return result;
	}

    @PostMapping(path ="/uploadHoroscopeImage", consumes={MediaType.MULTIPART_FORM_DATA_VALUE})
	public ResultResponse uploadHoroscopeImage(@RequestPart("userId") String userId,@RequestPart("file") @Nullable MultipartFile file) {
	    ResultResponse result = new ResultResponse();
	    try {
	        result = galleryService.uploadHoroscopeImage(userId, file);
	    } catch (Exception e) {
	        result.setCode(500);
	        result.setMessage(e.getMessage());
	        result.setStatus(ResponseStatus.FAILURE);
	    }
	    return result;
	}

    @DeleteMapping("/deleteHoroscope/{encodedUserId}")
    public ResultResponse deleteHoroscope(@PathVariable String encodedUserId) {
        ResultResponse response = new ResultResponse();
        try {
            response = galleryService.deleteHoroscope(encodedUserId);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }
}
