package com.uravugal.matrimony.repositories;

import com.uravugal.matrimony.enums.ActiveStatus;
import com.uravugal.matrimony.models.ShortlistedProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
