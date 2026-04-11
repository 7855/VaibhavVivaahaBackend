package com.uravugal.matrimony.repositories;

import com.uravugal.matrimony.enums.ActiveStatus;
import com.uravugal.matrimony.models.ShortlistedProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ShortlistedProfileRepository extends JpaRepository<ShortlistedProfile, Long> {
    Page<ShortlistedProfile> findByShortlistedByAndIsActive(Long userId, ActiveStatus status, Pageable pageable);
    Page<ShortlistedProfile> findByShortlistedUserIdAndIsActive(Long userId, ActiveStatus status, Pageable pageable);
    
    // Check if a user has already shortlisted another user
    ShortlistedProfile findByShortlistedByAndShortlistedUserIdAndIsActive(
        Long shortlistedBy, 
        Long shortlistedUserId, 
        ActiveStatus status
    );

    ShortlistedProfile findByShortlistedByAndShortlistedUserId(
        Long shortlistedBy, 
        Long shortlistedUserId
    );

    @Query("""
            SELECT COUNT(s) > 0
            FROM ShortlistedProfile s
            WHERE s.shortlistedBy = :by
            AND s.shortlistedUserId = :userId
            AND s.isActive = :status
            """)
    boolean existsByShortlistedByAndShortlistedUserIdAndIsActive(
            @Param("by") Long by,
            @Param("userId") Long userId,
            @Param("status") ActiveStatus status);

}
