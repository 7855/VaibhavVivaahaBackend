package com.uravugal.matrimony.repositories;

import com.uravugal.matrimony.models.PaymentLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PaymentLogRepository extends JpaRepository<PaymentLog, Long> {

    /** The full trail of one order, oldest first — reads top to bottom as a story. */
    List<PaymentLog> findByOrderIdOrderByIdAsc(String orderId);

    /** One day's rows, newest first. */
    List<PaymentLog> findByLogDateOrderByIdDesc(LocalDate logDate, Pageable pageable);

    /** Retention: everything before the cutoff day goes. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM PaymentLog p WHERE p.logDate < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDate cutoff);
}
