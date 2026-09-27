package com.uravugal.matrimony.repositories;

import com.uravugal.matrimony.models.PaymentOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {

    Optional<PaymentOrder> findByOrderId(String orderId);

    /** Newest reusable order for the same purchase, so a double-tap doesn't mint a second session. */
    List<PaymentOrder> findByUserIdAndPurposeAndOrderStatusOrderByIdDesc(Long userId, String purpose, String orderStatus);

    Page<PaymentOrder> findAllByOrderByIdDesc(Pageable pageable);
    Page<PaymentOrder> findByOrderStatusOrderByIdDesc(String orderStatus, Pageable pageable);
    Page<PaymentOrder> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);
    Page<PaymentOrder> findByUserIdAndOrderStatusOrderByIdDesc(Long userId, String orderStatus, Pageable pageable);

    /**
     * The fulfilment claim. Returns 1 for exactly one caller per order — whoever gets 1
     * activates the plan; everyone else (duplicate webhook, concurrent status poll) gets 0
     * and does nothing. This single conditional UPDATE replaces any need for row locks.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PaymentOrder p SET p.orderStatus = 'PAID', p.paymentStatus = 'SUCCESS', "
            + "p.fulfilledAt = :fulfilledAt, p.fulfilledBy = :source, p.updatedAt = :fulfilledAt "
            + "WHERE p.orderId = :orderId AND p.fulfilledAt IS NULL")
    int markPaid(@Param("orderId") String orderId, @Param("source") String source,
                 @Param("fulfilledAt") LocalDateTime fulfilledAt);

    /**
     * 🔴 fulfilledAt is passed IN, not CURRENT_TIMESTAMP. That resolved to the DATABASE's
     * clock while expiresAt and paymentTime are written from Java in IST, so a paid order
     * could read as fulfilled 5h30m BEFORE it was created. Every timestamp on this table is
     * now IST wall-clock, written by Java — see CashfreeService.istNow().
     */

    /**
     * ACTIVE orders whose window has closed — the ones to ASK Cashfree about before
     * writing them off. Ordered oldest first so a backlog drains in the order it built up.
     */
    @Query("SELECT p FROM PaymentOrder p WHERE p.orderStatus = 'ACTIVE' "
            + "AND p.expiresAt IS NOT NULL AND p.expiresAt < :now ORDER BY p.id ASC")
    java.util.List<PaymentOrder> findStaleActive(@Param("now") LocalDateTime now);

    /** Nightly housekeeping — local only, no Cashfree call. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PaymentOrder p SET p.orderStatus = 'EXPIRED', p.updatedAt = CURRENT_TIMESTAMP "
            + "WHERE p.orderStatus = 'ACTIVE' AND p.expiresAt IS NOT NULL AND p.expiresAt < :now")
    int expireStale(@Param("now") LocalDateTime now);
}
