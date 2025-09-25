package com.uravugal.matrimony.repositories;

import com.uravugal.matrimony.models.UserReport;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface UserReportRepository extends JpaRepository<UserReport, Long> {
    @Query("SELECT ur FROM UserReport ur ORDER BY ur.reportedAt DESC")
    List<UserReport> findAllOrderByReportedAtDesc();
    
    UserReport findByReportedByUserIdAndReportedUserId(Long reportedByUserId, Long reportedUserId);
}
