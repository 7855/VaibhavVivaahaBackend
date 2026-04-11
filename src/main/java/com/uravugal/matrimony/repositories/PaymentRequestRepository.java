package com.uravugal.matrimony.repositories;

import com.uravugal.matrimony.enums.PaymentRequestStatus;
import com.uravugal.matrimony.models.PaymentRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface PaymentRequestRepository extends JpaRepository<PaymentRequestEntity, Long> {
    List<PaymentRequestEntity> findByUserIdAndStatusNot(Long userId, PaymentRequestStatus status);
    Optional<PaymentRequestEntity> findTopByUserIdOrderByCreatedAtDesc(Long userId);

    List<PaymentRequestEntity> findByStatus(PaymentRequestStatus status);

    Page<PaymentRequestEntity> findByStatusOrderByCreatedAtDesc(PaymentRequestStatus status, Pageable pageable);
    Page<PaymentRequestEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
