package com.uravugal.matrimony.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.uravugal.matrimony.models.Features;

public interface FeaturesRepository extends JpaRepository<Features, Long> {

    Optional<Features> findById(Long id);

    Features findByCode(String string);
    
}
