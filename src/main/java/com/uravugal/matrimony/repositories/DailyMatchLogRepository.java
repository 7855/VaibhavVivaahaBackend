package com.uravugal.matrimony.repositories;

import com.uravugal.matrimony.models.DailyMatchLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DailyMatchLogRepository extends JpaRepository<DailyMatchLog, Long> {

    /** [suggestedUserId, MAX(sentOn)] per profile ever suggested to this member. */
    @Query("SELECT l.suggestedUserId, MAX(l.sentOn) FROM DailyMatchLog l "
         + "WHERE l.receiverId = :receiverId GROUP BY l.suggestedUserId")
    List<Object[]> findLastSuggestedByReceiver(@Param("receiverId") Long receiverId);

    /** [suggestedUserId, MAX(sentOn)] for profiles whose NAME went in this member's push. */
    @Query("SELECT l.suggestedUserId, MAX(l.sentOn) FROM DailyMatchLog l "
         + "WHERE l.receiverId = :receiverId AND l.headline = true GROUP BY l.suggestedUserId")
    List<Object[]> findLastHeadlinedByReceiver(@Param("receiverId") Long receiverId);

    boolean existsByReceiverIdAndSentOn(Long receiverId, LocalDate sentOn);

    @Modifying
    @Query("DELETE FROM DailyMatchLog l WHERE l.sentOn < :before")
    int deleteOlderThan(@Param("before") LocalDate before);
}
