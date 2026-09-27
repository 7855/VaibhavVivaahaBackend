package com.uravugal.matrimony.models;

import java.time.LocalDateTime;

import com.uravugal.matrimony.enums.ChatStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

@Data
@Entity
@Table(name = "conversations", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_one_id", "user_two_id"})
})
public class Conversation extends GenericEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_one_id", nullable = false)
    private Long userOne;

    @Column(name = "user_two_id", nullable = false)
    private Long userTwo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChatStatus status = ChatStatus.PENDING;

    @Column(name = "initiated_by", nullable = false)
    private Long initiatedBy;

    /**
     * Per-participant "deleted from my chat list" flags.
     *
     * Deleting a chat used to flip the SHARED GenericEntity.isActive to N, which hid the thread
     * for BOTH people — so anyone could send abusive messages and then erase the conversation
     * from the recipient's list too. These flags scope the hide to the person who asked for it;
     * the other side is unaffected and the `chats` rows are never touched, so message history is
     * still fully retained for moderation.
     *
     * Nullable on purpose: existing rows predate this column and read as "not deleted".
     * Cleared for both sides when a new message arrives (see ChatService), so a reply brings the
     * thread back — the same behaviour members expect from WhatsApp.
     */
    @Column(name = "deleted_by_user_one")
    private Boolean deletedByUserOne;

    @Column(name = "deleted_by_user_two")
    private Boolean deletedByUserTwo;

    /** True when the given user has hidden this conversation from their own list. */
    public boolean isDeletedFor(Long userId) {
        if (userId == null) return false;
        if (userId.equals(userOne)) return Boolean.TRUE.equals(deletedByUserOne);
        if (userId.equals(userTwo)) return Boolean.TRUE.equals(deletedByUserTwo);
        return false;
    }

    /** @return false when the user is not a participant (caller should reject the request). */
    public boolean markDeletedFor(Long userId) {
        if (userId == null) return false;
        if (userId.equals(userOne)) { deletedByUserOne = Boolean.TRUE; return true; }
        if (userId.equals(userTwo)) { deletedByUserTwo = Boolean.TRUE; return true; }
        return false;
    }

    /** A new message un-hides the thread for both participants. */
    public void clearDeletedFlags() {
        deletedByUserOne = Boolean.FALSE;
        deletedByUserTwo = Boolean.FALSE;
    }
}
