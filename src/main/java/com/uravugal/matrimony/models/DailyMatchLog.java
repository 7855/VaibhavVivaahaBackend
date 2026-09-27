package com.uravugal.matrimony.models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

/**
 * One row per profile the 8 AM Daily Match job suggested to a member.
 *
 * This is the job's memory. Without it each run reshuffled the same caste pool from scratch,
 * so a member with 12 candidates was named the same "top match" on half of their mornings,
 * and one with 3 candidates got the same three names every single day (measured over 14 days
 * of production: 716/718 had 14 sends and 3 distinct bodies). The picker now reads this table
 * to prefer profiles never suggested, then the least recently suggested.
 *
 * Rows older than {@code DailyMatchScheduler.KEEP_DAYS} are trimmed by the job itself.
 */
@Entity
@Data
@Table(name = "daily_match_log",
       indexes = @Index(name = "idx_dml_receiver_sent", columnList = "receiverId, sentOn"))
public class DailyMatchLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "receiverId", nullable = false)
    private Long receiverId;

    @Column(name = "suggestedUserId", nullable = false)
    private Long suggestedUserId;

    /** Calendar day of the send (IST wall date, see the scheduler's note on LocalDate.now()). */
    @Column(name = "sentOn", nullable = false)
    private LocalDate sentOn;

    /** True for the ONE pick whose name appears in the push body. */
    @Column(name = "headline", nullable = false)
    private boolean headline;
}
