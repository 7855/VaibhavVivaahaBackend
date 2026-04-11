package com.uravugal.matrimony.repositories;

import com.uravugal.matrimony.models.BannedIdentifier;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BannedIdentifierRepository extends JpaRepository<BannedIdentifier, Long> {
    BannedIdentifier findByMobile(String mobile);
    BannedIdentifier findByEmail(String email);
}