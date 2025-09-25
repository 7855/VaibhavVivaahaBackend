package com.uravugal.matrimony.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.uravugal.matrimony.models.HiddenFieldEntity;

@Repository
public interface HiddenFieldRepository extends JpaRepository<HiddenFieldEntity, Long> {
    List<HiddenFieldEntity> findByUserId(Long userId);
    Optional<HiddenFieldEntity> findByUserIdAndFieldName(Long userId, String fieldName);
    boolean existsByUserIdAndFieldName(Long userId, String fieldName);
}
