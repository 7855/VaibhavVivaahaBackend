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
    
    long countByFollowerIdAndStatusAndIsActive(Long followerId, ConnectionStatus status, ActiveStatus activeStatus);
    long countByFollowingIdAndStatusAndIsActive(Long followingId, ConnectionStatus status, ActiveStatus activeStatus);
    long countByFollowerIdAndIsActive(Long followerId, ActiveStatus activeStatus);
}
    