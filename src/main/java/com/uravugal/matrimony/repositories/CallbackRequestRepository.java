package com.uravugal.matrimony.repositories;

import com.uravugal.matrimony.models.CallbackRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CallbackRequestRepository extends JpaRepository<CallbackRequest, Long> {
    Page<CallbackRequest> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);
    Page<CallbackRequest> findByStatusOrderByIdDesc(String status, Pageable pageable);
    Page<CallbackRequest> findAllByOrderByIdDesc(Pageable pageable);

    // Dedup — block duplicate NEW requests for the same user/plan combo
    CallbackRequest findFirstByUserIdAndPlanInterestedAndStatus(
            Long userId, String planInterested, String status);
}