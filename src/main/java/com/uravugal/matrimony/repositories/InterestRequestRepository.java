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

    /**
     * Every member this user has an interest row with, in EITHER direction and ANY status.
     * Used to keep already-connected, pending and declined pairs out of "new match" suggestions.
     */
    @Query("SELECT CASE WHEN i.interestSend = :userId THEN i.interestReceived ELSE i.interestSend END "
         + "FROM InterestRequest i WHERE i.interestSend = :userId OR i.interestReceived = :userId")
    List<Long> findPartnerIds(@Param("userId") Long userId);
    Page<InterestRequest> findByInterestReceived(Long interestReceived, Pageable pageable);
    
    Page<InterestRequest> findByInterestReceivedAndAcceptStatus(Long interestReceived, ApprovalStatus status, Pageable pageable);
    
    List<InterestRequest> findByInterestReceivedAndAcceptStatus(Long interestReceived, ApprovalStatus status);
    List<InterestRequest> findByInterestSendAndAcceptStatus(Long interestSend, ApprovalStatus status);
    
    Page<InterestRequest> findByInterestSendAndAcceptStatusNot(Long interestSend, ApprovalStatus status, Pageable pageable);
    
    Page<InterestRequest> findByInterestSendAndAcceptStatus(Long interestSend, ApprovalStatus status, Pageable pageable);

    Page<InterestRequest> findByInterestSend(Long interestSend, Pageable pageable);
    
    boolean existsByInterestSendAndInterestReceived(Long interestSend, Long interestReceived);

    Optional<InterestRequest> findByInterestSendAndInterestReceived(Long interestSend, Long interestReceived);

    @Query("SELECT i.acceptStatus FROM InterestRequest i " +
    "WHERE i.interestSend = :senderId AND i.interestReceived = :receiverId")
    Optional<ApprovalStatus> findAcceptStatusBySenderAndReceiver(
     @Param("senderId") Long senderId, 
     @Param("receiverId") Long receiverId);

     /**
      * Every interest row between two members, newest first.
      *
      * There can legitimately be TWO: the unique constraint is on (interestSend, interestReceived),
      * so A→B and B→A are separate rows and both can exist once each member has expressed interest
      * in the other. This must therefore return a List — see findBetweenUsers below.
      */
     @Query("""
SELECT i
FROM InterestRequest i
WHERE
   (i.interestSend = :viewer AND i.interestReceived = :profile)
OR (i.interestSend = :profile AND i.interestReceived = :viewer)
ORDER BY i.updatedAt DESC, i.id DESC
""")
List<InterestRequest> findAllBetweenUsers(
    @Param("viewer") Long viewer,
    @Param("profile") Long profile
);

/**
 * The single most relevant interest between two members.
 *
 * 🔴 This used to be a `@Query` returning Optional directly, which threw
 * IncorrectResultSizeDataAccessException ("Query did not return a unique result: 2 results were
 * returned") the moment BOTH directions existed — e.g. A sends and cancels, then B sends. That
 * surfaced as a 500 with a blank Profile Detail screen, and because five features gate on this
 * method it also broke contact reveal, horoscope view, voice-call requests and star match for
 * that pair.
 *
 * An APPROVED row wins regardless of age: if the two are connected, that is the state every
 * caller cares about. Otherwise the most recently updated row is used.
 *
 * Kept as a default method with the original name and signature so no caller changed.
 */
default Optional<InterestRequest> findBetweenUsers(Long viewer, Long profile) {
    List<InterestRequest> rows = findAllBetweenUsers(viewer, profile);
    if (rows.isEmpty()) return Optional.empty();
    return rows.stream()
            .filter(r -> r.getAcceptStatus() == ApprovalStatus.APPROVED)
            .findFirst()
            .or(() -> Optional.of(rows.get(0)));
}

    long countByInterestSend(Long interestSend);

    @Query("SELECT COUNT(i) FROM InterestRequest i " +
           "WHERE (i.interestSend = :userId OR i.interestReceived = :userId) " +
           "AND i.acceptStatus = :status")
    long countMatchesByUserIdAndStatus(@Param("userId") Long userId, @Param("status") ApprovalStatus status);


}
