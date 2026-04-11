package com.uravugal.matrimony.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.uravugal.matrimony.models.UserLead;

@Repository
public interface UserLeadRepository extends JpaRepository<UserLead, Long> {
    UserLead findByEmail(String email);
    UserLead findByVerificationToken(String verificationToken);
    void deleteByEmail(String email);
}