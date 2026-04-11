package com.uravugal.matrimony.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.uravugal.matrimony.models.SavedSearchesEntity;
import com.uravugal.matrimony.repositories.SaveSearchRepository;
import java.util.List;
import java.util.Optional;

public interface SaveSearchRepository extends JpaRepository<SavedSearchesEntity, Long>{
    
    List<SavedSearchesEntity> findByUserIdAndIsActive(Long userId, String activeStatus);
    Optional<SavedSearchesEntity> findByIdAndUserId(Long id, Long userId);
    void deleteByIdAndUserId(Long id, Long userId);
}
