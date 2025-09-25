package com.uravugal.matrimony.repositories;

import com.uravugal.matrimony.models.Notification;
import com.uravugal.matrimony.enums.ActiveStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    
    // Find all notifications for a specific user
    List<Notification> findByReceiverIdOrderByCreatedAtDesc(Long receiverId);
    
    // Find unread notifications for a specific user
    List<Notification> findByReceiverIdAndIsReadOrderByCreatedAtDesc(Long receiverId, ActiveStatus isRead);
    
    // Mark notifications as read
    @Query("UPDATE Notification n SET n.isRead = :status WHERE n.receiverId = :userId AND n.isRead = :activeStatus")
    int markNotificationsAsRead(@Param("userId") Long userId, @Param("status") ActiveStatus status, @Param("activeStatus") ActiveStatus activeStatus);
    
    // Get notifications by category for a specific user
    List<Notification> findByReceiverIdAndNotificationCategoryOrderByCreatedAtDesc(Long receiverId, String category);

    // Get unread notification count for user
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.receiverId = :userId AND n.isRead = :status")
    Long getUnreadNotificationCount(@Param("userId") Long userId, @Param("status") ActiveStatus status);

    // Delete notifications by user ID
    @Query("DELETE FROM Notification n WHERE n.receiverId = :userId")
    void deleteByReceiverId(@Param("userId") Long userId);

    // Delete notification by ID
    @SuppressWarnings("null")
    @Query("DELETE FROM Notification n WHERE n.notificationId = :notificationId")
    void deleteById(@Param("notificationId") Long notificationId);

    void deleteAllByReceiverId(Long userId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = 'Y' WHERE n.receiverId = :receiverId")
    void markAllAsReadByReceiverId(@Param("receiverId") Long receiverId);

    
}
