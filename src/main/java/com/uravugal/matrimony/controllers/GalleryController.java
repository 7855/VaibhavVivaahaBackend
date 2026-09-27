package com.uravugal.matrimony.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
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

    /** Promote an existing gallery image to the member's profile photo (no re-upload). */
    @PutMapping("/setAsProfileImage/{encodedUserId}/{galleryId}")
    public ResultResponse setAsProfileImage(@PathVariable String encodedUserId, @PathVariable Long galleryId) {
        ResultResponse response = new ResultResponse();
        try {
            response = galleryService.setGalleryImageAsProfile(encodedUserId, galleryId);
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

    /** Replace one gallery photo in place (same row, order preserved). Parts: userId (raw numeric), galleryId, file. */
    @PostMapping(path = "/replaceGalleryImage", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public ResultResponse replaceGalleryImage(@RequestPart("userId") String userId,
            @RequestPart("galleryId") String galleryId,
            @RequestPart("file") MultipartFile file) {
        ResultResponse result = new ResultResponse();
        try {
            result = galleryService.replaceGalleryImage(userId, Long.parseLong(galleryId), file);
        } catch (Exception e) {
            result.setCode(500);
            result.setMessage(e.getMessage());
            result.setStatus(ResponseStatus.FAILURE);
        }
        return result;
    }

    /**
     * Store a file, return its URL. Nothing is attached to any member — the admin panel uses
     * this so a photo can be chosen before the member exists, and so an uploaded file and a
     * pasted URL travel the same path from there on.
     */
    @PostMapping(path = "/uploadImage", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public ResultResponse uploadImage(@RequestPart("file") MultipartFile file,
                                      @RequestPart(value = "folder", required = false) String folder) {
        try {
            return galleryService.uploadImage(file, folder);
        } catch (Exception e) {
            ResultResponse r = new ResultResponse();
            r.setCode(500); r.setStatus(ResponseStatus.FAILURE);
            r.setMessage("Error uploading file: " + e.getMessage());
            return r;
        }
    }

    /** Attach an already-hosted image to a member's gallery. ⚠ RAW numeric userId. */
    @PostMapping("/addGalleryImageByUrl/{userId}")
    public ResultResponse addGalleryImageByUrl(@PathVariable String userId,
                                               @RequestBody java.util.Map<String, String> body) {
        try {
            return galleryService.addGalleryImageByUrl(userId, body.get("imageUrl"));
        } catch (Exception e) {
            ResultResponse r = new ResultResponse();
            r.setCode(500); r.setStatus(ResponseStatus.FAILURE);
            r.setMessage("Error adding gallery image: " + e.getMessage());
            return r;
        }
    }

    /** Point the profile photo (or horoscope, with ?horoscope=true) at a hosted image. */
    @PostMapping("/setImageUrl/{userId}")
    public ResultResponse setImageUrl(@PathVariable String userId,
                                      @RequestParam(defaultValue = "false") boolean horoscope,
                                      @RequestBody java.util.Map<String, String> body) {
        try {
            return galleryService.setImageUrl(userId, body.get("imageUrl"), horoscope);
        } catch (Exception e) {
            ResultResponse r = new ResultResponse();
            r.setCode(500); r.setStatus(ResponseStatus.FAILURE);
            r.setMessage("Error updating image: " + e.getMessage());
            return r;
        }
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
