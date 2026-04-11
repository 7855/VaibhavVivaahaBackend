package com.uravugal.matrimony.repositories;

import com.uravugal.matrimony.models.ServiceRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, Long> {
    Page<ServiceRequest> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);
    Page<ServiceRequest> findByStatusOrderByIdDesc(String status, Pageable pageable);
    Page<ServiceRequest> findByRequestTypeOrderByIdDesc(String requestType, Pageable pageable);
    Page<ServiceRequest> findAllByOrderByIdDesc(Pageable pageable);

    // Dedup helpers — block duplicate pending requests
    ServiceRequest findFirstByUserIdAndRequestTypeAndTargetUserIdAndStatus(
            Long userId, String requestType, Long targetUserId, String status);

    ServiceRequest findFirstByUserIdAndRequestTypeAndStatus(
            Long userId, String requestType, String status);
}
