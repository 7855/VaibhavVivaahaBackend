package com.uravugal.matrimony.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.uravugal.matrimony.models.DeletedAccount;

@Repository
public interface DeletedAccountRepository extends JpaRepository<DeletedAccount, Long> {
}
