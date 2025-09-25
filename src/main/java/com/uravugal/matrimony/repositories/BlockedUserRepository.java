package com.uravugal.matrimony.repositories;

import com.uravugal.matrimony.models.BlockedUser;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BlockedUserRepository extends JpaRepository<BlockedUser, Long> {
    Page<BlockedUser> findByBlockedByUserIdOrderByBlockedAtDesc(String blockedByUserId, Pageable pageable);
    
    @Query("SELECT b FROM BlockedUser b WHERE (b.blockedByUserId = :user1 AND b.blockedUserId = :user2) OR (b.blockedByUserId = :user2 AND b.blockedUserId = :user1)")
    BlockedUser findByUsersEitherDirection(@Param("user1") Long user1, @Param("user2") Long user2);
    
    void deleteById(@SuppressWarnings("null") Long id);

    @Query("SELECT bu FROM BlockedUser bu ORDER BY bu.blockedAt DESC")
    List<BlockedUser> findAllOrderByBlockedAtDesc();

    BlockedUser findByBlockedByUserIdAndBlockedUserId(Long blockedByUserId, Long blockedUserId);

}
