package com.uravugal.matrimony.repositories;

import com.uravugal.matrimony.enums.ActiveStatus;
import com.uravugal.matrimony.models.RestrictedFieldRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface RestrictedFieldRequestRepository extends JpaRepository<RestrictedFieldRequest, Long> {
    // Page<RestrictedFieldRequest> findByRequestedToAndIsActive(Long userId, boolean isActive, Pageable pageable);
    // Page<RestrictedFieldRequest> findByRequestedByAndIsActive(Long userId, boolean isActive, Pageable pageable);
    Page<RestrictedFieldRequest> findByRequestedByAndRequestedToAndFieldType(Long requestedBy, Long requestedTo, String fieldType, Pageable pageable);
    
    List<RestrictedFieldRequest> findByRequestedToAndIsActive(Long userId, ActiveStatus isActive);
    List<RestrictedFieldRequest> findByRequestedByAndIsActive(Long userId, ActiveStatus isActive);
    boolean existsByRequestedByAndRequestedToAndFieldType(Long requestedBy, Long requestedTo, String fieldType);

    RestrictedFieldRequest findByRequestedByAndRequestedToAndFieldTypeAndIsActive(Long requestedBy, Long requestedTo,
            String fieldType, ActiveStatus y);

    List<RestrictedFieldRequest> findByRequestedByAndRequestedToAndIsActive(Long requestById, Long requestToId,
            ActiveStatus y);
}
