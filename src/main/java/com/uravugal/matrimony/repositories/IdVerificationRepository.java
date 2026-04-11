package com.uravugal.matrimony.repositories;

import com.uravugal.matrimony.models.IdVerification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IdVerificationRepository extends JpaRepository<IdVerification, Long> {

    IdVerification findFirstByUserIdOrderByIdDesc(Long userId);

    IdVerification findFirstByUserIdAndStatusOrderByIdDesc(Long userId, String status);

    Page<IdVerification> findAllByOrderByIdDesc(Pageable pageable);

    Page<IdVerification> findByStatusOrderByIdDesc(String status, Pageable pageable);
}