package com.uravugal.matrimony.repositories;

import com.uravugal.matrimony.models.EducationVerification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EducationVerificationRepository extends JpaRepository<EducationVerification, Long> {

    EducationVerification findFirstByUserIdOrderByIdDesc(Long userId);

    EducationVerification findFirstByUserIdAndStatusOrderByIdDesc(Long userId, String status);

    Page<EducationVerification> findAllByOrderByIdDesc(Pageable pageable);

    Page<EducationVerification> findByStatusOrderByIdDesc(String status, Pageable pageable);
}