package com.uravugal.matrimony.repositories;

import com.uravugal.matrimony.models.IncomeVerification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IncomeVerificationRepository extends JpaRepository<IncomeVerification, Long> {

    /** Latest submission for a given user (regardless of status). */
    IncomeVerification findFirstByUserIdOrderByIdDesc(Long userId);

    /** Block a user from spamming uploads while one is still PENDING. */
    IncomeVerification findFirstByUserIdAndStatusOrderByIdDesc(Long userId, String status);

    /** Admin list views — all rows newest first. */
    Page<IncomeVerification> findAllByOrderByIdDesc(Pageable pageable);

    /** Admin list filtered by status. */
    Page<IncomeVerification> findByStatusOrderByIdDesc(String status, Pageable pageable);
}