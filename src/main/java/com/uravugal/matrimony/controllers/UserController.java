package com.uravugal.matrimony.controllers;

import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.dtos.UserFilterRequest;
import com.uravugal.matrimony.dtos.UserDetailUpdateRequest;
import com.uravugal.matrimony.dtos.UserProfileRequest;
import com.uravugal.matrimony.dtos.OtpVerificationRequest;
import com.google.firebase.database.annotations.Nullable;
import com.uravugal.matrimony.dtos.ChangePinRequest;
import com.uravugal.matrimony.enums.Gender;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.UserEntity;
import com.uravugal.matrimony.services.UserService;
import com.uravugal.matrimony.services.UserConnectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/user")
@CrossOrigin(origins = "*")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserConnectionService userConnectionService;

    @PostMapping("/changePin")
    public ResultResponse changePin(@RequestBody ChangePinRequest request) {
        ResultResponse resp = new ResultResponse();
        try {
            resp = userService.changePin(request.getMobileNumber(), request.getPin());
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @PostMapping("/verifyOtp")
    public ResultResponse verifyOtp(@RequestBody OtpVerificationRequest request) {
        ResultResponse resp = new ResultResponse();
        try {
            resp = userService.verifyOtp(request.getMobileNumber(), request.getOtp());
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @PostMapping("/sendOtp/{mobileNumber}")
    public ResultResponse sendOtp(@PathVariable String mobileNumber) {
        ResultResponse resp = new ResultResponse();
        try {
            resp = userService.sendOtp(mobileNumber);
        } catch (Exception e) { 
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @PostMapping("/saveOrUpdateUser")
    public ResultResponse saveOrUpdateUser(@RequestBody UserEntity userEntity) {
        ResultResponse resp = new ResultResponse();
        try {
            resp = userService.saveOrUpdateUser(userEntity);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @PostMapping("/createUser")
    public ResultResponse createUser(@RequestBody UserProfileRequest userEntity) {
        ResultResponse resp = new ResultResponse();
        try {
            resp = userService.createUser(userEntity);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @GetMapping("/getAllUsers")
    public ResultResponse getAllUsers() {
        ResultResponse resp = new ResultResponse();
        try {
            resp = userService.getAllUsers();
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @GetMapping("/getUserByUserId/{encodedId}")
    public ResultResponse getUserById(@PathVariable String encodedId) {
        ResultResponse resp = new ResultResponse();
        try {
            // Decode the Base64 encoded ID
            // System.out.println("=======================================>"+encodedId);
            // String decodedId = new String(Base64.getDecoder().decode(encodedId));
            resp = userService.getUserById(encodedId);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @GetMapping("/getProfileDetailByUserId/{userId}")
    public ResultResponse getProfileDetailByUserId(@PathVariable Long userId) {
        ResultResponse resp = new ResultResponse();
        try {
            // Decode the Base64 encoded ID
            System.out.println("=======================================>"+userId);
            resp = userService.getProfileDetailByUserId(userId);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @GetMapping("/getUserDetailByCasteId/{casteId}/{gender}")
    public ResultResponse getUserDetailByCaste(@PathVariable Integer casteId, @PathVariable Gender gender) {
        ResultResponse resp = new ResultResponse();
        try {
            resp = userService.getUserDetailByCaste(casteId, gender);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    // Near You
    @GetMapping("/getUserDetailByCasteIdAndLocation/{casteId}/{gender}/{location}")
    public ResultResponse getUserDetailByCasteIdAndLocation(@PathVariable Integer casteId,
            @PathVariable Gender gender, @PathVariable String location) {
        ResultResponse resp = new ResultResponse();
        try {
            gender = gender == Gender.M ? Gender.F : Gender.M;
            resp = userService.getUserDetailByCasteIdAndLocation(casteId, gender, location);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    // Daily recommendations

    @GetMapping("/getDailyShuffledUsersByCaste/{casteId}/{gender}")
    public ResultResponse getDailyShuffledUsersByCaste(@PathVariable Integer casteId, @PathVariable Gender gender) {
        ResultResponse resp = new ResultResponse();
        try {
            gender = gender == Gender.M ? Gender.F : Gender.M;
            resp = userService.getDailyShuffledUsersByCaste(casteId, gender);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }


    @GetMapping("/getTop30NewUsers/{casteId}/{gender}")
    public ResultResponse getTop30NewUsers(@PathVariable Integer casteId, @PathVariable Gender gender) {
        ResultResponse resp = new ResultResponse();
        try {
            gender = gender == Gender.M ? Gender.F : Gender.M;
            resp = userService.getTop30NewUsers(casteId,gender);    
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @GetMapping("/getShuffledUsersWithPagination/{casteId}")
    public ResultResponse getShuffledUsersWithPagination(@PathVariable Integer casteId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        ResultResponse resp = new ResultResponse();
        try {
            resp = userService.getShuffledUsersWithPagination(casteId, page, size);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @GetMapping("/getTenShuffledUsers/{gender}/{casteId}")
    public ResultResponse getTenShuffledUsers(
            @PathVariable Gender gender,
            @PathVariable Integer casteId) {
        ResultResponse resp = new ResultResponse();
        try {
            resp = userService.getTenShuffledUsers(gender, casteId);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @PostMapping("/filterUsers")
    public ResultResponse filterUsers(@RequestBody UserFilterRequest filterRequest) {
        ResultResponse resp = new ResultResponse();
        try {
            resp = userService.filterUsers(filterRequest);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @PostMapping("/updateUserDetailSection")
    public ResultResponse updateUserDetailSection(@RequestBody UserDetailUpdateRequest request) {
        ResultResponse resp = new ResultResponse();

        try {
            resp = userService.updateUserDetailSection(request);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @PostMapping("/login")
    public ResultResponse login(@RequestBody UserEntity loginRequest) {
        ResultResponse resp = new ResultResponse();
        try {
            resp = userService.login(loginRequest);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @GetMapping("/getUserConnectionCounts/{encodedId}")
    public ResultResponse getUserConnectionCounts(@PathVariable String encodedId) {
        ResultResponse resp = new ResultResponse();
        try {
            resp = userConnectionService.getUserConnectionCounts(encodedId);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }


    @GetMapping("/followUser/{followerId}/{followingId}")
    public ResultResponse followUser(@PathVariable Long followerId, @PathVariable Long followingId) {
        ResultResponse resp = new ResultResponse();
        try {
            resp = userConnectionService.followUser(followerId, followingId);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @PutMapping("/lastseen/{encodedId}")
    public ResultResponse updateLastSeen(@PathVariable String encodedId) {
        ResultResponse resp = new ResultResponse();
        try {
            resp = userConnectionService.updateLastSeen(encodedId);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @GetMapping("/getUserOnlineStatus/{userId}")
    public ResultResponse getUserOnlineStatus(@PathVariable Long userId) {
        ResultResponse resp = new ResultResponse();
        try {
            resp = userConnectionService.getUserOnlineStatus(userId);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @GetMapping("/getUserPaidStatus/{encodedUserId}")
    public ResultResponse getUserPaidStatus(@PathVariable String encodedUserId) {
        ResultResponse resp = new ResultResponse();
        try {
            resp = userService.getUserPaidStatus(encodedUserId);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }

    @PostMapping(path ="/updateProfileImage", consumes={MediaType.MULTIPART_FORM_DATA_VALUE})
	public ResultResponse updateProfileImage(@RequestPart("userId") String userId,@RequestPart("file") @Nullable MultipartFile file) {
	    ResultResponse result = new ResultResponse();
	    try {
	        result = userService.updateProfileImage(userId, file);
	    } catch (Exception e) {
	        result.setCode(500);
	        result.setMessage(e.getMessage());
	        result.setStatus(ResponseStatus.FAILURE);
	    }
	    return result;
	}

    @GetMapping("/getProfileDetailByMemberId/{memberId}/{gender}/{casteId}")
    public ResultResponse getProfileDetailByMemberId(@PathVariable String memberId, @PathVariable Gender gender, @PathVariable Integer casteId) {
        ResultResponse resp = new ResultResponse();
        try {
            resp = userService.getProfileDetailByMemberId(memberId, gender, casteId);
        } catch (Exception e) {
            resp.setCode(500);
            resp.setMessage("Something Went Wrong. " + e.getMessage());
            resp.setStatus(ResponseStatus.FAILURE);
        }
        return resp;
    }
    
}
