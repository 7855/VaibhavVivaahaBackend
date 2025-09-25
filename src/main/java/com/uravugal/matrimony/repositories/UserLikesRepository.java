package com.uravugal.matrimony.repositories;

import com.uravugal.matrimony.models.UserLikes;

import jakarta.transaction.Transactional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserLikesRepository extends JpaRepository<UserLikes, Long> {
    // Custom query methods can be added here if needed
     @Query("SELECT COUNT(u) > 0 FROM UserLikes u WHERE u.likedBy = :likedBy AND u.likedTo = :likedTo")
    boolean existsByLikerAndLikedUser(@Param("likedBy") Long likedBy, @Param("likedTo") Long likedTo);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM UserLikes u WHERE u.likedBy = :likedBy AND u.likedTo = :likedTo")
    void deleteByLikerAndLikedUser(@Param("likedBy") Long likedBy, @Param("likedTo") Long likedTo);
    
}