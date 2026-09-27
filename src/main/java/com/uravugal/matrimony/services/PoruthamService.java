package com.uravugal.matrimony.services;

import java.util.Base64;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.uravugal.matrimony.dtos.MatchRequestDto;
import com.uravugal.matrimony.dtos.MatchResponseDto;
import com.uravugal.matrimony.dtos.ResultResponse;
import com.uravugal.matrimony.enums.ApprovalStatus;
import com.uravugal.matrimony.enums.ResponseStatus;
import com.uravugal.matrimony.models.InterestRequest;
import com.uravugal.matrimony.repositories.InterestRequestRepository;
import com.uravugal.matrimony.utils.PoruthamEngine;
import com.uravugal.matrimony.utils.StarInfo;

/**
 * Star Match (Thirumana Porutham). The calculation itself lives in the pure
 * {@link PoruthamEngine}; this service only resolves names, enforces the consent gate and
 * shapes the response. The engine needs exactly four inputs: the girl's and boy's birth star
 * and rasi. Birth date, time and place are not used by the 10-porutham method.
 */
@Service
public class PoruthamService {

    @Autowired
    private InterestRequestRepository interestRequestRepository;

    /** Any spelling of a star → 1..27 (0 when unknown). Kept public: other services call it. */
    public int getStarId(String starName) {
        return StarInfo.starId(starName);
    }

    public ResultResponse evaluate(MatchRequestDto req, boolean persist) {
        ResultResponse resultResponse = new ResultResponse();

        // Star Match against another member's real profile (the profile-initiated flow from
        // ProfileDetail.tsx) requires their consent via an accepted interest first — same rule
        // as Voice Call (see ServiceRequestService.createRequest). The standalone horoscope
        // utility (settingsPage.tsx / QuickAccessFAB.tsx) sends neither id, so it's untouched.
        if (req.getRequesterUserId() != null && req.getViewedUserId() != null) {
            try {
                Long requesterUserId = Long.parseLong(new String(Base64.getDecoder().decode(req.getRequesterUserId())));
                Long viewedUserId = Long.parseLong(req.getViewedUserId());
                Optional<InterestRequest> interest = interestRequestRepository.findBetweenUsers(requesterUserId, viewedUserId);
                if (interest.isEmpty() || interest.get().getAcceptStatus() != ApprovalStatus.APPROVED) {
                    resultResponse.setCode(403);
                    resultResponse.setStatus(ResponseStatus.FAILURE);
                    resultResponse.setMessage("INTEREST_NOT_APPROVED");
                    return resultResponse;
                }
            } catch (Exception e) {
                resultResponse.setCode(400);
                resultResponse.setStatus(ResponseStatus.FAILURE);
                resultResponse.setMessage("Invalid requester/viewed user id");
                return resultResponse;
            }
        }

        MatchRequestDto.Profile bride = req.getBride();
        MatchRequestDto.Profile groom = req.getGroom();
        if (bride == null || groom == null) {
            resultResponse.setCode(400);
            resultResponse.setStatus(ResponseStatus.FAILURE);
            resultResponse.setMessage("Both bride and groom details are required");
            return resultResponse;
        }

        int brideStar = StarInfo.starId(bride.getStar());
        int groomStar = StarInfo.starId(groom.getStar());
        int brideRasi = StarInfo.rasiId(bride.getRasi());
        int groomRasi = StarInfo.rasiId(groom.getRasi());

        // Star is mandatory for 8 of the 10 poruthams; refuse rather than grade against nothing.
        if (brideStar == 0 || groomStar == 0) {
            resultResponse.setCode(400);
            resultResponse.setStatus(ResponseStatus.FAILURE);
            resultResponse.setMessage(brideStar == 0
                    ? "Bride's star is missing or not recognised: " + bride.getStar()
                    : "Groom's star is missing or not recognised: " + groom.getStar());
            return resultResponse;
        }

        MatchResponseDto response = PoruthamEngine.evaluate(brideStar, groomStar, brideRasi, groomRasi);

        resultResponse.setCode(200);
        resultResponse.setData(response);
        resultResponse.setMessage("Horoscope Match Results Fetched Successfully.");
        resultResponse.setStatus(ResponseStatus.SUCCESS);
        return resultResponse;
    }
}
