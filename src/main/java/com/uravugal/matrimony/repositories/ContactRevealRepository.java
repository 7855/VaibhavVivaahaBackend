package com.uravugal.matrimony.repositories;

import com.uravugal.matrimony.models.ContactReveal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContactRevealRepository extends JpaRepository<ContactReveal, Long> {
    boolean existsByViewerIdAndRevealedUserId(Long viewerId, Long revealedUserId);

    List<ContactReveal> findByViewerIdOrderByCreatedAtDesc(Long viewerId);
}