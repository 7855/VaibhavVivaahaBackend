package com.uravugal.matrimony.repositories;

import com.uravugal.matrimony.models.ProfileBoost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ProfileBoostRepository extends JpaRepository<ProfileBoost, Long> {

    /** Get user's current active boost (if any). */
    ProfileBoost findFirstByUserIdAndStatusOrderByExpiresAtDesc(Long userId, String status);

    /** Find all boosts that have expired but are still marked ACTIVE (for the scheduler). */
    List<ProfileBoost> findAllByStatusAndExpiresAtBefore(String status, LocalDateTime now);

    /** Admin: paginated list newest first. */
    Page<ProfileBoost> findAllByOrderByIdDesc(Pageable pageable);

    /** Admin: filtered by status. */
    Page<ProfileBoost> findByStatusOrderByIdDesc(String status, Pageable pageable);
}