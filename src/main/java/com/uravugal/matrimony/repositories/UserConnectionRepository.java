package com.uravugal.matrimony.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.uravugal.matrimony.enums.ActiveStatus;
import com.uravugal.matrimony.enums.ConnectionStatus;
import com.uravugal.matrimony.models.UserConnectionEntity;

@Repository
public interface UserConnectionRepository extends JpaRepository<UserConnectionEntity, Long> {
    List<UserConnectionEntity> findByFollowerIdAndStatusAndIsActive(Long followerId, ConnectionStatus status, ActiveStatus activeStatus);
    List<UserConnectionEntity> findByFollowingIdAndStatusAndIsActive(Long followingId, ConnectionStatus status, ActiveStatus activeStatus);
    Optional<UserConnectionEntity> findByFollowerIdAndFollowingId(Long followerId, Long followingId);

    /**
     * All rows for a follower→following pair.
     *
     * `user_connections` has NO unique constraint on (followerId, followingId), and followUser()
     * used to insert without checking, so a double-tap could create two rows. Once that happened
     * the Optional finder above threw IncorrectResultSizeDataAccessException and unfollow failed
     * with a 500 forever — the member could never undo the follow. followUser is now guarded, and
     * unfollow uses this List form so any pre-existing duplicates are cleaned up rather than
     * becoming a permanent error.
     */
    List<UserConnectionEntity> findAllByFollowerIdAndFollowingId(Long followerId, Long followingId);
    
    long countByFollowerIdAndStatusAndIsActive(Long followerId, ConnectionStatus status, ActiveStatus activeStatus);
    long countByFollowingIdAndStatusAndIsActive(Long followingId, ConnectionStatus status, ActiveStatus activeStatus);
    long countByFollowerIdAndIsActive(Long followerId, ActiveStatus activeStatus);
}
    