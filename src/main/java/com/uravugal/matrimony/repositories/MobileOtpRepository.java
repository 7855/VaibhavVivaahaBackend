package com.uravugal.matrimony.repositories;

import com.uravugal.matrimony.models.MobileOtp;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MobileOtpRepository extends JpaRepository<MobileOtp, Long> {
    MobileOtp findByMobile(String mobile);
}
