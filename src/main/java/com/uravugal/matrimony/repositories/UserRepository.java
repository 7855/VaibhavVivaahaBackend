package com.uravugal.matrimony.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.uravugal.matrimony.dtos.FilteredUserPlanView;
import com.uravugal.matrimony.enums.ActiveStatus;
import com.uravugal.matrimony.enums.IsUser;
import com.uravugal.matrimony.enums.EmploymentType;
import com.uravugal.matrimony.enums.Gender;
import com.uravugal.matrimony.models.UserEntity;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {

    List<UserEntity> findAllByCasteIdAndIsActiveAndIsUserNot(Integer caste, ActiveStatus active, IsUser excludeRole);

    @Query("SELECT u FROM UserEntity u WHERE u.casteId = :casteId AND LOWER(u.location) LIKE LOWER(CONCAT('%', :location, '%')) AND u.isActive = :active AND u.isUser != com.uravugal.matrimony.enums.IsUser.ADM")
    List<UserEntity> findAllByCasteIdAndLocationAndIsActive(@Param("casteId") Integer casteId, @Param("location") String location, @Param("active") ActiveStatus active);

    List<UserEntity> findTop30ByCasteIdAndIsUserNotOrderByCreatedAtDesc(Integer casteId, IsUser excludeRole);

    @Query(value = "SELECT u FROM UserEntity u WHERE u.casteId = :casteId AND u.isActive = :active AND u.isUser != com.uravugal.matrimony.enums.IsUser.ADM ORDER BY RAND()", nativeQuery = false)
    List<UserEntity> findAllByCasteIdAndIsActiveOrderByRandom(@Param("casteId") Integer caste,
            @Param("active") ActiveStatus active);

    List<UserEntity> findAllByIsActive(ActiveStatus active);

    List<UserEntity> findAllByIsActiveAndIsUserNot(ActiveStatus active, IsUser excludeRole);

    @Query(value = "SELECT u FROM UserEntity u WHERE u.isActive = :active ORDER BY RAND()", nativeQuery = false)
    List<UserEntity> findAllByIsActiveOrderByRandom(@Param("active") ActiveStatus active);

    @Query(value = "SELECT u FROM UserEntity u WHERE u.gender = :gender AND u.casteId = :casteId AND u.isActive = :active ORDER BY RAND()", nativeQuery = false)
    List<UserEntity> findAllByGenderAndCasteIdAndIsActiveOrderByRandom(
            @Param("gender") Gender gender,
            @Param("casteId") Integer casteId,
            @Param("active") ActiveStatus active);

    @Query(value = """
            SELECT
                u.userId,
                u.firstName,
                u.lastName,
                u.age,
                u.location,
                u.profileImage,
                u.isUser,
                ud.height,
                ud.occupation,
                ud.annualIncome,
                sp.id AS subscriptionPlanId,
                sp.title AS subscriptionTitle,
                CASE WHEN u.id_verified = 1 THEN 1 ELSE 0 END AS idVerified,
                CASE WHEN u.education_verified = 1 THEN 1 ELSE 0 END AS educationVerified,
                CASE WHEN u.income_verified = 1 THEN 1 ELSE 0 END AS incomeVerified,
                CASE WHEN pb.id IS NOT NULL THEN 1 ELSE 0 END AS hasActiveBoost
            FROM users u
            JOIN user_details ud ON u.userId = ud.userId
            LEFT JOIN user_subscriptions us
                   ON u.userId = us.userId AND us.status = 'ACTIVE'
            LEFT JOIN subscription_plans sp
                   ON us.subscriptionPlanId = sp.id
            LEFT JOIN profile_boosts pb
                   ON u.userId = pb.userId AND pb.status = 'ACTIVE' AND pb.expiresAt > NOW()
            WHERE u.isActive = 'Y'
              AND u.isUser != 'ADM'
              AND (:gender IS NULL OR u.gender = :gender)
              AND (:casteId IS NULL OR u.casteId = :casteId)
              AND (:minAge IS NULL OR CAST(u.age AS UNSIGNED) >= :minAge)
              AND (:maxAge IS NULL OR CAST(u.age AS UNSIGNED) <= :maxAge)
              AND (
                   (:minAnnualIncome IS NULL AND :maxAnnualIncome IS NULL)
                OR (:minAnnualIncome IS NOT NULL AND :maxAnnualIncome IS NULL
                     AND CAST(ud.annualIncome AS UNSIGNED) >= :minAnnualIncome)
                OR (:minAnnualIncome IS NULL AND :maxAnnualIncome IS NOT NULL
                     AND CAST(ud.annualIncome AS UNSIGNED) <= :maxAnnualIncome)
                OR (:minAnnualIncome IS NOT NULL AND :maxAnnualIncome IS NOT NULL
                     AND CAST(ud.annualIncome AS UNSIGNED) BETWEEN :minAnnualIncome AND :maxAnnualIncome)
              )
              AND (:location IS NULL OR u.location = :location)
              AND (COALESCE(:occupation) IS NULL OR ud.occupation IN (:occupation))
              AND (COALESCE(:employedAt) IS NULL OR ud.employedAt IN (:employedAt))
              AND (COALESCE(:degree) IS NULL OR ud.degree IN (:degree))
              AND (
                  COALESCE(:star) IS NULL
                  OR JSON_UNQUOTE(JSON_EXTRACT(ud.astroInfo, '$[0].star')) IN (:star)
              )
              AND (
                  COALESCE(:dosham) IS NULL
                  OR JSON_UNQUOTE(JSON_EXTRACT(ud.astroInfo, '$[0].dosham')) IN (:dosham)
              )
              AND (
                  :profileImageStatus IS NULL
                  OR :profileImageStatus = 'N'
                  OR (:profileImageStatus = 'Y' AND u.profileImage IS NOT NULL)
              )
              AND (
                  :profilesWithHoroscope IS NULL
                  OR :profilesWithHoroscope = 'N'
                  OR (:profilesWithHoroscope = 'Y' AND ud.horoscope IS NOT NULL)
              )
            ORDER BY
               CASE WHEN pb.id IS NOT NULL THEN 1 ELSE 0 END DESC,
               CASE WHEN sp.id IN (5, 6) THEN 1 ELSE 0 END DESC,
               CASE sp.id
                    WHEN 6 THEN 5
                    WHEN 5 THEN 4
                    WHEN 4 THEN 3
                    WHEN 3 THEN 2
                    WHEN 2 THEN 1
                    ELSE 0
               END DESC,
               MOD(u.userId,7)
            """, nativeQuery = true)
    List<FilteredUserPlanView> advanceFilter(
            @Param("gender") String gender,
            @Param("casteId") Integer casteId,
            @Param("minAge") Integer minAge,
            @Param("maxAge") Integer maxAge,
            @Param("minAnnualIncome") Integer minAnnualIncome,
            @Param("maxAnnualIncome") Integer maxAnnualIncome,
            @Param("occupation") List<String> occupation,
            @Param("location") String location,
            @Param("employedAt") List<String> employedAt,
            @Param("degree") List<String> degree,
            @Param("profileImageStatus") String profileImageStatus,
            @Param("star") List<String> star,
            @Param("dosham") List<String> dosham,
            @Param("profilesWithHoroscope") String profilesWithHoroscope);

    UserEntity findByMobile(String mobile);

    @Query("SELECT u FROM UserEntity u WHERE u.email = :email AND u.mobile IS NOT NULL ORDER BY u.userId DESC")
    java.util.List<UserEntity> findRegisteredByEmail(@org.springframework.data.repository.query.Param("email") String email);

    default UserEntity findByEmail(String email) {
        java.util.List<UserEntity> list = findRegisteredByEmail(email);
        return list.isEmpty() ? null : list.get(0);
    }

    UserEntity findByResetToken(String resetToken);

    UserEntity findByRefreshToken(String refreshToken);

    Optional<UserEntity> findByUserId(Long id);

    List<UserEntity> findAllByCasteIdAndGenderAndIsActiveAndIsUserNot(Integer casteId, Gender gender, ActiveStatus y, IsUser excludeRole);

    List<UserEntity> findTop30ByCasteIdAndGenderAndIsActiveAndIsUserNotOrderByCreatedAtDesc(Integer casteId, Gender gender,
            ActiveStatus y, IsUser excludeRole);

    @Query("SELECT u FROM UserEntity u WHERE u.casteId = :casteId AND u.gender = :gender AND LOWER(TRIM(u.location)) LIKE LOWER(CONCAT('%', TRIM(:location), '%')) AND u.isActive = :active AND u.isUser != com.uravugal.matrimony.enums.IsUser.ADM")
    List<UserEntity> findAllByCasteIdAndGenderAndLocationAndIsActive(@Param("casteId") Integer casteId, @Param("gender") Gender gender, @Param("location") String location, @Param("active") ActiveStatus active);

    List<UserEntity> findAllByCasteIdAndGenderAndIsActiveAndIsUserNotAndLocationIgnoreCase(Integer casteId, Gender gender,
            ActiveStatus y, IsUser excludeRole, String location);

    @Query(value = "SELECT memberId FROM users ORDER BY memberId DESC LIMIT 1", nativeQuery = true)
    String findLastMemberId();

    @Query(value = """
            SELECT
                u.userId,
                u.firstName,
                u.lastName,
                u.age,
                u.location,
                u.profileImage,
                u.isUser,
                ud.height,
                ud.occupation,
                ud.annualIncome,
                sp.id AS subscriptionPlanId,
                sp.title AS subscriptionTitle,
                CASE WHEN u.id_verified = 1 THEN 1 ELSE 0 END AS idVerified,
                CASE WHEN u.education_verified = 1 THEN 1 ELSE 0 END AS educationVerified,
                CASE WHEN u.income_verified = 1 THEN 1 ELSE 0 END AS incomeVerified,
                CASE WHEN pb.id IS NOT NULL THEN 1 ELSE 0 END AS hasActiveBoost
            FROM users u
            JOIN user_details ud ON u.userId = ud.userId
            LEFT JOIN user_subscriptions us
                   ON u.userId = us.userId AND us.status = 'ACTIVE'
            LEFT JOIN subscription_plans sp
                   ON us.subscriptionPlanId = sp.id
            LEFT JOIN profile_boosts pb
                   ON u.userId = pb.userId AND pb.status = 'ACTIVE' AND pb.expiresAt > NOW()
            WHERE u.isActive = :active
              AND u.isUser != 'ADM'
              AND (:gender IS NULL OR u.gender = :gender)
              AND (:casteId IS NULL OR u.casteId = :casteId)
              AND (:minAge IS NULL OR CAST(u.age AS UNSIGNED) >= :minAge)
              AND (:maxAge IS NULL OR CAST(u.age AS UNSIGNED) <= :maxAge)
              AND (:location IS NULL OR u.location = :location)
              AND (
                    :profileImageStatus IS NULL
                    OR :profileImageStatus = 'N'
                    OR (:profileImageStatus = 'Y' AND u.profileImage IS NOT NULL)
                  )
            ORDER BY
               CASE WHEN pb.id IS NOT NULL THEN 1 ELSE 0 END DESC,
               CASE WHEN sp.id IN (5, 6) THEN 1 ELSE 0 END DESC,
               CASE sp.id
                    WHEN 6 THEN 5
                    WHEN 5 THEN 4
                    WHEN 4 THEN 3
                    WHEN 3 THEN 2
                    WHEN 2 THEN 1
                    ELSE 0
               END DESC,
               MOD(u.userId,7)
            """, nativeQuery = true)
    List<FilteredUserPlanView> normalFilter(
            @Param("active") String active,
            @Param("gender") String gender,
            @Param("casteId") Integer casteId,
            @Param("minAge") Integer minAge,
            @Param("maxAge") Integer maxAge,
            @Param("location") String location,
            @Param("profileImageStatus") String profileImageStatus);

    Optional<UserEntity> findByMemberIdAndGenderAndCasteId(String memberId, Gender gender, Integer casteId);

    @Query(value = """
            SELECT
                u.userId,
                CONCAT(u.firstName, ' ', COALESCE(u.lastName, '')) AS username,
                u.memberId AS profileId,
                u.location,
                c.casteName,
                ROUND(
                    (
                        (CASE WHEN u.firstName IS NOT NULL AND u.firstName != '' THEN 1 ELSE 0 END) +
                        (CASE WHEN u.lastName IS NOT NULL AND u.lastName != '' THEN 1 ELSE 0 END) +
                        (CASE WHEN u.email IS NOT NULL AND u.email != '' THEN 1 ELSE 0 END) +
                        (CASE WHEN u.mobile IS NOT NULL AND u.mobile != '' THEN 1 ELSE 0 END) +
                        (CASE WHEN u.dob IS NOT NULL THEN 1 ELSE 0 END) +
                        (CASE WHEN u.location IS NOT NULL AND u.location != '' THEN 1 ELSE 0 END) +
                        (CASE WHEN u.profileImage IS NOT NULL AND u.profileImage != '' THEN 1 ELSE 0 END) +
                        (CASE WHEN ud.about IS NOT NULL AND ud.about != '' THEN 1 ELSE 0 END) +
                        (CASE WHEN ud.familyInfo IS NOT NULL AND ud.familyInfo != '' THEN 1 ELSE 0 END) +
                        (CASE WHEN ud.basicInfo IS NOT NULL AND ud.basicInfo != '' THEN 1 ELSE 0 END) +
                        (CASE WHEN ud.hobbies IS NOT NULL AND ud.hobbies != '' THEN 1 ELSE 0 END) +
                        (CASE WHEN ud.languages IS NOT NULL AND ud.languages != '' THEN 1 ELSE 0 END) +
                        (CASE WHEN ud.height IS NOT NULL AND ud.height != '' THEN 1 ELSE 0 END) +
                        (CASE WHEN ud.weight IS NOT NULL AND ud.weight != '' THEN 1 ELSE 0 END) +
                        (CASE WHEN ud.occupation IS NOT NULL AND ud.occupation != '' THEN 1 ELSE 0 END) +
                        (CASE WHEN ud.annualIncome IS NOT NULL AND ud.annualIncome != '' THEN 1 ELSE 0 END) +
                        (CASE WHEN ud.degree IS NOT NULL AND ud.degree != '' THEN 1 ELSE 0 END) +
                        (CASE WHEN ud.horoscope IS NOT NULL AND ud.horoscope != '' THEN 1 ELSE 0 END)
                    ) * 100.0 / 18
                ) AS profileCompletedPercentage,
                sp.title AS subscriptionPlan,
                u.userStatus AS approvalStatus,
                u.createdAt AS createdOn,
                u.isActive,
                u.isUser
            FROM users u
            LEFT JOIN user_details ud ON u.userId = ud.userId
            LEFT JOIN castes c ON u.casteId = c.id
            LEFT JOIN (
                SELECT us1.userId, us1.subscriptionPlanId
                FROM user_subscriptions us1
                WHERE us1.id = (
                    SELECT us2.id FROM user_subscriptions us2
                    WHERE us2.userId = us1.userId
                    ORDER BY us2.createdAt DESC LIMIT 1
                )
            ) latest_sub ON u.userId = latest_sub.userId
            LEFT JOIN subscription_plans sp ON latest_sub.subscriptionPlanId = sp.id
            WHERE (:userType IS NULL OR u.isUser = :userType)
              AND (:userStatus IS NULL OR u.userStatus = :userStatus)
            ORDER BY u.createdAt DESC
            """, countQuery = "SELECT COUNT(*) FROM users u WHERE (:userType IS NULL OR u.isUser = :userType) AND (:userStatus IS NULL OR u.userStatus = :userStatus)", nativeQuery = true)
    Page<Object[]> findAllUsersForAdmin(@org.springframework.data.repository.query.Param("userType") String userType, @org.springframework.data.repository.query.Param("userStatus") String userStatus, Pageable pageable);

    @Query(value = """
            SELECT
                u.userId,
                u.firstName,
                u.lastName,
                u.gender,
                u.dob,
                u.age,
                ud.weight,
                JSON_UNQUOTE(JSON_EXTRACT(ud.familyInfo, '$.fatherName')) AS fatherName,
                JSON_UNQUOTE(JSON_EXTRACT(ud.familyInfo, '$.motherName')) AS motherName,
                JSON_UNQUOTE(JSON_EXTRACT(ud.languages, '$[0]')) AS motherLanguage,
                u.profileCreated,
                u.userStatus,
                u.email,
                u.mobile,
                u.location,
                JSON_UNQUOTE(JSON_EXTRACT(ud.basicInfo, '$.placeOfBirth')) AS placeOfBirth,
                u.pin,
                u.casteId,
                c.casteName,
                JSON_UNQUOTE(JSON_EXTRACT(ud.astroInfo, '$[0].star')) AS star,
                JSON_UNQUOTE(JSON_EXTRACT(ud.astroInfo, '$[0].dosham')) AS dosham,
                ud.degree,
                ud.educationInDetail,
                ud.employedAt,
                ud.occupation,
                ud.jobPlace,
                ud.annualIncome,
                u.profileImage,
                ud.horoscope
            FROM users u
            LEFT JOIN user_details ud ON u.userId = ud.userId
            LEFT JOIN castes c ON u.casteId = c.id
            WHERE u.userId = :userId
            """, nativeQuery = true)
    List<Object[]> findUserDetailForAdmin(@Param("userId") Long userId);

}
