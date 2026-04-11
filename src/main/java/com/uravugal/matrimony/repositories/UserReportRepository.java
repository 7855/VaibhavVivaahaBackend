package com.uravugal.matrimony.repositories;

import com.uravugal.matrimony.models.UserReport;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface UserReportRepository extends JpaRepository<UserReport, Long> {
    @Query("SELECT ur FROM UserReport ur ORDER BY ur.reportedAt DESC")
    List<UserReport> findAllOrderByReportedAtDesc();

    UserReport findByReportedByUserIdAndReportedUserId(Long reportedByUserId, Long reportedUserId);

    @Query(value = "SELECT ur.*, " +
        "CONCAT(reporter.firstName, ' ', reporter.lastName) AS reporterName, " +
        "CONCAT(reported.firstName, ' ', reported.lastName) AS reportedName " +
        "FROM userReports ur " +
        "LEFT JOIN users reporter ON ur.reportedByUserId = reporter.userId " +
        "LEFT JOIN users reported ON ur.reportedUserId = reported.userId " +
        "ORDER BY ur.reportedAt DESC",
        countQuery = "SELECT COUNT(*) FROM userReports",
        nativeQuery = true)
    Page<Object[]> findAllReportsForAdmin(Pageable pageable);
}
