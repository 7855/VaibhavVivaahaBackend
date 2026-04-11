package com.uravugal.matrimony.repositories;

import com.uravugal.matrimony.models.FamilyLogin;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FamilyLoginRepository extends JpaRepository<FamilyLogin, Long> {
    FamilyLogin findByMobileAndStatus(String mobile, String status);
    List<FamilyLogin> findByPrimaryUserIdAndStatusOrderByIdDesc(Long primaryUserId, String status);
    List<FamilyLogin> findAllByOrderByIdDesc();
}