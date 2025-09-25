package com.uravugal.matrimony.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.uravugal.matrimony.models.UserDeviceInformation;

public interface PushNotificationRepository extends JpaRepository<UserDeviceInformation, Long> {
    List<UserDeviceInformation> findAllByUserId(Long userId);
    
    Optional<UserDeviceInformation> findByUserIdAndDeviceId(Long userId, String deviceId);
    
    Optional<UserDeviceInformation> findByFcmToken(String fcmToken);
    
    @Modifying
    @Transactional
    @Query("DELETE FROM UserDeviceInformation udi WHERE udi.userId = :userId AND udi.deviceId = :deviceId")
    void deleteByUserIdAndDeviceId(@Param("userId") Long userId, @Param("deviceId") String deviceId);
}
