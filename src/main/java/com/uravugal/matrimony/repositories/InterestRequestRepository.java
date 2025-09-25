package com.uravugal.matrimony.repositories;

import com.uravugal.matrimony.enums.ApprovalStatus;
import com.uravugal.matrimony.models.InterestRequest;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InterestRequestRepository extends JpaRepository<InterestRequest, Long> {
    Page<InterestRequest> findByInterestReceived(Long interestReceived, Pageable pageable);
    
    Page<InterestRequest> findByInterestReceivedAndAcceptStatus(Long interestReceived, ApprovalStatus status, Pageable pageable);
    
    List<InterestRequest> findByInterestReceivedAndAcceptStatus(Long interestReceived, ApprovalStatus status);
    List<InterestRequest> findByInterestSendAndAcceptStatus(Long interestSend, ApprovalStatus status);
    
    Page<InterestRequest> findByInterestSendAndAcceptStatusNot(Long interestSend, ApprovalStatus status, Pageable pageable);
    
    Page<InterestRequest> findByInterestSendAndAcceptStatus(Long interestSend, ApprovalStatus status, Pageable pageable);
    
    boolean existsByInterestSendAndInterestReceived(Long interestSend, Long interestReceived);

    @Query("SELECT i.acceptStatus FROM InterestRequest i " +
    "WHERE i.interestSend = :senderId AND i.interestReceived = :receiverId")
    Optional<ApprovalStatus> findAcceptStatusBySenderAndReceiver(
     @Param("senderId") Long senderId, 
     @Param("receiverId") Long receiverId);
}
