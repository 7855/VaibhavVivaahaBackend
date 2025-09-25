package com.uravugal.matrimony.services;

import com.uravugal.matrimony.dtos.MailboxUserDetail;
import com.uravugal.matrimony.dtos.PaginatedResultResponse;
import com.uravugal.matrimony.dtos.PaginationData;
import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ActiveStatus;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.ShortlistedProfile;
import com.uravugal.matrimony.models.UserDetailEntity;
import com.uravugal.matrimony.models.UserEntity;
import com.uravugal.matrimony.repositories.ShortlistedProfileRepository;
import com.uravugal.matrimony.repositories.UserDetailRepository;
import com.uravugal.matrimony.repositories.UserRepository;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

@Service
public class ShortlistedProfileService {

    @Autowired
    private ShortlistedProfileRepository shortlistedProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserDetailRepository userDetailRepository;

    public ResultResponse insertShortlistedProfile(ShortlistedProfile shortlistedProfile) {
        ResultResponse response = new ResultResponse();
        try {
            // Validate input parameters
            if (shortlistedProfile.getShortlistedBy() == null || shortlistedProfile.getShortlistedUserId() == null) {
                response.setCode(400);
                response.setMessage("Both shortlistedBy and shortlistedUserId are required");
                response.setStatus(ResponseStatus.FAILURE);
                return response;
            }
            
            // Check if both users exist
            if (userRepository.findById(shortlistedProfile.getShortlistedBy()).isEmpty()) {
                response.setCode(404);
                response.setMessage("Shortlisting user not found");
                response.setStatus(ResponseStatus.FAILURE);
                return response;
            }
            if (userRepository.findById(shortlistedProfile.getShortlistedUserId()).isEmpty()) {
                response.setCode(404);
                response.setMessage("Shortlisted user not found");
                response.setStatus(ResponseStatus.FAILURE);
                return response;
            }
            
            // Check if user is trying to shortlist themselves
            if (shortlistedProfile.getShortlistedBy().equals(shortlistedProfile.getShortlistedUserId())) {
                response.setCode(400);
                response.setMessage("Cannot shortlist yourself");
                response.setStatus(ResponseStatus.FAILURE);
                return response;
            }

            // Check if user has already shortlisted this profile
            ShortlistedProfile existingShortlist = shortlistedProfileRepository.findByShortlistedByAndShortlistedUserIdAndIsActive(
                shortlistedProfile.getShortlistedBy(),
                shortlistedProfile.getShortlistedUserId(),
                ActiveStatus.Y
            );
            
            if (existingShortlist != null) {
                response.setCode(400);
                response.setMessage("Profile already shortlisted");
                response.setStatus(ResponseStatus.FAILURE);
                return response;
            }

            
            ShortlistedProfile savedProfile = shortlistedProfileRepository.save(shortlistedProfile);
            
            response.setCode(200);
            response.setMessage("Profile shortlisted successfully");
            response.setStatus(ResponseStatus.SUCCESS);
            response.setData(savedProfile);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Something Went Wrong. " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }



    public PaginatedResultResponse getShortlistedProfiles(String encodedId, Integer page, Integer size) {
        PaginatedResultResponse resp = new PaginatedResultResponse();
        try {
            String decodedId = new String(Base64.getDecoder().decode(encodedId));
            Long userId = Long.parseLong(decodedId);
            Page<ShortlistedProfile> profiles = shortlistedProfileRepository.findByShortlistedByAndIsActive(
                userId, ActiveStatus.Y, 
                PageRequest.of(page != null ? page : 0, size != null ? size : 10)
            );
            
            if (profiles.isEmpty()) {
                resp.setCode(404);
                resp.setMessage("No shortlisted profiles found");
                resp.setStatus(ResponseStatus.FAILURE);
                return resp;
            }

            // Fetch user details for each shortlisted profile
            List<MailboxUserDetail> userDetails = profiles.getContent().stream()
                .map(profile -> {
                    MailboxUserDetail detail = new MailboxUserDetail();
                    UserEntity user = userRepository.findById(profile.getShortlistedUserId()).orElse(null);
                    UserDetailEntity userDetail = userDetailRepository.findByUserId(profile.getShortlistedUserId());
                    
                    if (user != null) {
                        detail.setUserId(user.getUserId());
                        detail.setFirstName(user.getFirstName());
                        detail.setLastName(user.getLastName());
                        detail.setProfileImage(user.getProfileImage());
                        
                        if (userDetail != null) {
                            detail.setAge(user.getDob() != null ? 
                                (int) java.time.Period.between(user.getDob(), java.time.LocalDate.now()).getYears() : null);
                            detail.setDegree(userDetail.getDegree());
                            detail.setAnnualIncome(userDetail.getAnnualIncome());
                            detail.setOccupation(userDetail.getOccupation());
                            detail.setLocation(userDetail.getPresentAddress());
                        }
                        detail.setShortlistedId(profile.getId());
                    }
                    return detail;
                })
                .collect(Collectors.toList());

            resp.setCode(200);
            resp.setMessage("Shortlisted profiles fetched successfully");
            resp.setStatus(ResponseStatus.SUCCESS);
            resp.setData(userDetails);
            
            PaginationData pagination = new PaginationData();
            pagination.setTotalPages(profiles.getTotalPages());
            pagination.setTotalElements(profiles.getTotalElements());
            pagination.setCurrentPage(profiles.getNumber());
            pagination.setPageSize(profiles.getSize());
            resp.setPaginationData(pagination);
            
            return resp;
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Error fetching shortlisted profiles: " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
            return resp;
        }
    }

    public ResultResponse deleteShortlistedProfile(Long id) {
        ResultResponse resp = new ResultResponse();
        try {
            ShortlistedProfile profile = shortlistedProfileRepository.findById(id)
                .orElse(null);

            if(profile == null) {
                resp.setCode(404);
                resp.setMessage("Shortlisted profile not found");
                resp.setStatus(ResponseStatus.FAILURE);
                return resp;
            }

            shortlistedProfileRepository.delete(profile);
            resp.setCode(200);
            resp.setMessage("Shortlisted profile deleted successfully");
            resp.setStatus(ResponseStatus.SUCCESS);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Error deleting shortlisted profile: " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    public ResultResponse deleteShortlistedProfileByUsers(String encodedId, Long userId) {
        ResultResponse resp = new ResultResponse();
        try {
            // Decode the encoded ID
            String decodedId = new String(Base64.getDecoder().decode(encodedId));
            Long shortlistedBy = Long.parseLong(decodedId);

            // Find the profile to delete
            ShortlistedProfile profile = shortlistedProfileRepository.findByShortlistedByAndShortlistedUserIdAndIsActive(
                shortlistedBy, 
                userId, 
                ActiveStatus.Y
            );

            if(profile == null) {
                resp.setCode(404);
                resp.setMessage("Shortlisted profile not found");
                resp.setStatus(ResponseStatus.FAILURE);
                return resp;
            }

            shortlistedProfileRepository.delete(profile);
            resp.setCode(200);
            resp.setMessage("Shortlisted profile deleted successfully");
            resp.setStatus(ResponseStatus.SUCCESS);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Error deleting shortlisted profile: " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    public ResultResponse checkIfShortlisted(String encodedId, Long userId) {
        ResultResponse response = new ResultResponse();
        try {
            // Decode the encoded ID
            String decodedId = new String(Base64.getDecoder().decode(encodedId));
            Long shortlistedBy = Long.parseLong(decodedId);

            // Check if user has already shortlisted
            ShortlistedProfile profile = shortlistedProfileRepository.findByShortlistedByAndShortlistedUserIdAndIsActive(
                shortlistedBy, 
                userId, 
                ActiveStatus.Y
            );

            response.setCode(200);
            response.setMessage(profile != null ? "User is already shortlisted" : "User is not shortlisted");
            response.setStatus(profile != null ? ResponseStatus.SUCCESS : ResponseStatus.FAILURE);
            response.setData(profile != null);
        } catch (Exception e) {
            response.setCode(500);
            response.setMessage("Error checking shortlist status: " + e.getMessage());
            response.setStatus(ResponseStatus.FAILURE);
        }
        return response;
    }
}
