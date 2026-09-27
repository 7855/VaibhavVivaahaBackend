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

    /**
     * SUBCASTE FILTERING (applies to both advanceFilter and normalFilter):
     *
     *  WHERE  — an explicit subcaste filter also admits members who explicitly chose "Others".
     *           They may well BE the searched subcaste and simply didn't know it, so excluding
     *           them would shrink results for no good reason. Members with NO subcaste at all
     *           are NOT admitted — that is not an opt-in, and including them would make the
     *           filter a no-op against legacy data where every row is null.
     *  ORDER  — those "Others" members are then ranked LAST, so exact matches always come
     *           first. Driven by the scalar subcasteFilterApplied flag (0/1), never by the
     *           subcasteIds list: Hibernate expands an IN-list parameter once per occurrence,
     *           and referencing it more than twice corrupts native-query binding (it fails with
     *           a misleading "No argument for named parameter ':occupation'").
     *           When no subcaste filter is applied the flag is 0 and every row scores 0, so an
     *           unfiltered search never penalises members who chose "Others".
     *           This key sits ABOVE boost/plan-tier, so relevance groups the list while boost
     *           still orders profiles WITHIN each group.
     *
     * NOTE: do NOT put -- comments inside these query strings. Hibernate scans the raw SQL for
     * ":name" parameters without skipping comments, so a comment that merely mentions a
     * parameter registers a phantom binding and the whole query fails to parse at startup.
     */
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
              AND (
                  COALESCE(:subcasteIds) IS NULL
                  OR u.subcasteId IN (:subcasteIds)
                  OR u.subcasteId IN (SELECT so.id FROM subcastes so WHERE so.subcasteName = 'Others')
              )
              AND (:minAge IS NULL OR TIMESTAMPDIFF(YEAR, u.dob, CURDATE()) >= :minAge)
              AND (:maxAge IS NULL OR TIMESTAMPDIFF(YEAR, u.dob, CURDATE()) <= :maxAge)
              AND (
                   (:minAnnualIncome IS NULL AND :maxAnnualIncome IS NULL)
                OR (:minAnnualIncome IS NOT NULL AND :maxAnnualIncome IS NULL
                     AND (CASE WHEN ud.annualIncome REGEXP '^[0-9]+$' THEN CAST(ud.annualIncome AS UNSIGNED) ELSE CAST(REGEXP_SUBSTR(ud.annualIncome, '^[0-9]+') AS UNSIGNED) * 100000 END) >= :minAnnualIncome)
                OR (:minAnnualIncome IS NULL AND :maxAnnualIncome IS NOT NULL
                     AND (CASE WHEN ud.annualIncome REGEXP '^[0-9]+$' THEN CAST(ud.annualIncome AS UNSIGNED) ELSE CAST(REGEXP_SUBSTR(ud.annualIncome, '^[0-9]+') AS UNSIGNED) * 100000 END) <= :maxAnnualIncome)
                OR (:minAnnualIncome IS NOT NULL AND :maxAnnualIncome IS NOT NULL
                     AND (CASE WHEN ud.annualIncome REGEXP '^[0-9]+$' THEN CAST(ud.annualIncome AS UNSIGNED) ELSE CAST(REGEXP_SUBSTR(ud.annualIncome, '^[0-9]+') AS UNSIGNED) * 100000 END) BETWEEN :minAnnualIncome AND :maxAnnualIncome)
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
               CASE WHEN :subcasteFilterApplied = 0 THEN 0
                    WHEN u.subcasteId IN (SELECT so2.id FROM subcastes so2 WHERE so2.subcasteName = 'Others') THEN 1
                    ELSE 0 END ASC,
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
            @Param("subcasteIds") List<Long> subcasteIds,
            @Param("subcasteFilterApplied") Integer subcasteFilterApplied,
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
              AND (
                  COALESCE(:subcasteIds) IS NULL
                  OR u.subcasteId IN (:subcasteIds)
                  OR u.subcasteId IN (SELECT so.id FROM subcastes so WHERE so.subcasteName = 'Others')
              )
              AND (:minAge IS NULL OR TIMESTAMPDIFF(YEAR, u.dob, CURDATE()) >= :minAge)
              AND (:maxAge IS NULL OR TIMESTAMPDIFF(YEAR, u.dob, CURDATE()) <= :maxAge)
              AND (:location IS NULL OR u.location = :location)
              AND (COALESCE(:employedAt) IS NULL OR ud.employedAt IN (:employedAt))
              AND (
                    :profileImageStatus IS NULL
                    OR :profileImageStatus = 'N'
                    OR (:profileImageStatus = 'Y' AND u.profileImage IS NOT NULL)
                  )
            ORDER BY
               CASE WHEN :subcasteFilterApplied = 0 THEN 0
                    WHEN u.subcasteId IN (SELECT so2.id FROM subcastes so2 WHERE so2.subcasteName = 'Others') THEN 1
                    ELSE 0 END ASC,
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
            @Param("subcasteIds") List<Long> subcasteIds,
            @Param("subcasteFilterApplied") Integer subcasteFilterApplied,
            @Param("minAge") Integer minAge,
            @Param("maxAge") Integer maxAge,
            @Param("location") String location,
            @Param("employedAt") List<String> employedAt,
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
                u.isUser,
                u.resubmission_note
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
              AND (:plan IS NULL OR sp.title = :plan)
              AND (:search IS NULL
                   OR u.firstName LIKE CONCAT('%', :search, '%')
                   OR u.lastName  LIKE CONCAT('%', :search, '%')
                   OR CONCAT(u.firstName, ' ', u.lastName) LIKE CONCAT('%', :search, '%')
                   OR u.email     LIKE CONCAT('%', :search, '%')
                   OR u.mobile    LIKE CONCAT('%', :search, '%')
                   OR u.memberId  LIKE CONCAT('%', :search, '%'))
            ORDER BY u.createdAt DESC
            """,
            // ⚠ The count must see the SAME joins and predicates as the page, or the pager's
            // total is for a different set than the rows — hence the latest-sub join is repeated.
            countQuery = """
            SELECT COUNT(*) FROM users u
            LEFT JOIN (
                SELECT us1.userId, us1.subscriptionPlanId FROM user_subscriptions us1
                WHERE us1.id = (SELECT us2.id FROM user_subscriptions us2 WHERE us2.userId = us1.userId
                                ORDER BY us2.createdAt DESC LIMIT 1)
            ) latest_sub ON u.userId = latest_sub.userId
            LEFT JOIN subscription_plans sp ON latest_sub.subscriptionPlanId = sp.id
            WHERE (:userType IS NULL OR u.isUser = :userType)
              AND (:userStatus IS NULL OR u.userStatus = :userStatus)
              AND (:plan IS NULL OR sp.title = :plan)
              AND (:search IS NULL
                   OR u.firstName LIKE CONCAT('%', :search, '%')
                   OR u.lastName  LIKE CONCAT('%', :search, '%')
                   OR CONCAT(u.firstName, ' ', u.lastName) LIKE CONCAT('%', :search, '%')
                   OR u.email     LIKE CONCAT('%', :search, '%')
                   OR u.mobile    LIKE CONCAT('%', :search, '%')
                   OR u.memberId  LIKE CONCAT('%', :search, '%'))
            """, nativeQuery = true)
    Page<Object[]> findAllUsersForAdmin(@org.springframework.data.repository.query.Param("userType") String userType,
                                        @org.springframework.data.repository.query.Param("userStatus") String userStatus,
                                        @org.springframework.data.repository.query.Param("plan") String plan,
                                        @org.springframework.data.repository.query.Param("search") String search,
                                        Pageable pageable);

    @Query(value = """
            SELECT
                u.userId,
                u.firstName,
                u.lastName,
                u.gender,
                u.dob,
                u.age,
                ud.weight,
                NULLIF(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(ud.familyInfo, '$[0].father'),
                                      JSON_EXTRACT(ud.familyInfo, '$.fatherName'))), 'null') AS fatherName,
                NULLIF(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(ud.familyInfo, '$[0].mother'),
                                      JSON_EXTRACT(ud.familyInfo, '$.motherName'))), 'null') AS motherName,
                NULLIF(JSON_UNQUOTE(JSON_EXTRACT(ud.languages, '$[0]')), 'null') AS motherLanguage,
                u.profileCreated,
                u.userStatus,
                u.email,
                u.mobile,
                u.location,
                NULLIF(JSON_UNQUOTE(COALESCE(JSON_EXTRACT(ud.basicInfo, '$.place_of_birth'),
                                      JSON_EXTRACT(ud.basicInfo, '$.placeOfBirth'))), 'null') AS placeOfBirth,
                u.pin,
                u.casteId,
                c.casteName,
                NULLIF(JSON_UNQUOTE(JSON_EXTRACT(ud.astroInfo, '$[0].star')), 'null') AS star,
                NULLIF(JSON_UNQUOTE(JSON_EXTRACT(ud.astroInfo, '$[0].dosham')), 'null') AS dosham,
                ud.degree,
                ud.educationInDetail,
                ud.employedAt,
                ud.occupation,
                ud.jobPlace,
                ud.annualIncome,
                u.profileImage,
                ud.horoscope,
                ud.hobbies,
                ud.about,
                ud.height,
                NULLIF(JSON_UNQUOTE(JSON_EXTRACT(ud.astroInfo, '$[0].birth_time')), 'null') AS birthTime,
                NULLIF(JSON_UNQUOTE(JSON_EXTRACT(ud.astroInfo, '$[0].moon_sign')), 'null') AS moonSign,
                NULLIF(JSON_UNQUOTE(JSON_EXTRACT(ud.basicInfo, '$.physical_status')), 'null') AS physicalStatus,
                NULLIF(JSON_UNQUOTE(JSON_EXTRACT(ud.basicInfo, '$.marital_status')), 'null') AS maritalStatus,
                ud.presentAddress,
                ud.permanentAddress,
                NULLIF(JSON_UNQUOTE(JSON_EXTRACT(ud.familyInfo, '$[0].family_type')), 'null') AS familyType,
                NULLIF(JSON_UNQUOTE(JSON_EXTRACT(ud.familyInfo, '$[0].family_status')), 'null') AS familyStatus,
                NULLIF(JSON_UNQUOTE(JSON_EXTRACT(ud.familyInfo, '$[0].father_occupation')), 'null') AS fatherOccupation,
                NULLIF(JSON_UNQUOTE(JSON_EXTRACT(ud.familyInfo, '$[0].mother_occupation')), 'null') AS motherOccupation,
                NULLIF(JSON_UNQUOTE(JSON_EXTRACT(ud.familyInfo, '$[0].no_of_brother')), 'null') AS noOfBrothers,
                NULLIF(JSON_UNQUOTE(JSON_EXTRACT(ud.familyInfo, '$[0].brother_married')), 'null') AS brothersMarried,
                NULLIF(JSON_UNQUOTE(JSON_EXTRACT(ud.familyInfo, '$[0].no_of_sister')), 'null') AS noOfSisters,
                NULLIF(JSON_UNQUOTE(JSON_EXTRACT(ud.familyInfo, '$[0].sister_married')), 'null') AS sistersMarried,
                u.subcasteId,
                sc.subcasteName
            FROM users u
            LEFT JOIN user_details ud ON u.userId = ud.userId
            LEFT JOIN castes c ON u.casteId = c.id
            LEFT JOIN subcastes sc ON u.subcasteId = sc.id
            WHERE u.userId = :userId
            """, nativeQuery = true)
    List<Object[]> findUserDetailForAdmin(@Param("userId") Long userId);

    @Query(value = """
            SELECT
                u.userId, u.firstName, u.lastName, u.age, u.location,
                u.profileImage, u.isUser, ud.height, ud.occupation, ud.annualIncome,
                sp.id AS subscriptionPlanId, sp.title AS subscriptionTitle,
                CASE WHEN u.id_verified = 1 THEN 1 ELSE 0 END AS idVerified,
                CASE WHEN u.education_verified = 1 THEN 1 ELSE 0 END AS educationVerified,
                CASE WHEN u.income_verified = 1 THEN 1 ELSE 0 END AS incomeVerified,
                CASE WHEN pb.id IS NOT NULL THEN 1 ELSE 0 END AS hasActiveBoost
            FROM users u
            JOIN user_details ud ON u.userId = ud.userId
            LEFT JOIN user_subscriptions us ON u.userId = us.userId AND us.status = 'ACTIVE'
            LEFT JOIN subscription_plans sp ON us.subscriptionPlanId = sp.id
            LEFT JOIN profile_boosts pb ON u.userId = pb.userId AND pb.status = 'ACTIVE' AND pb.expiresAt > NOW()
            WHERE u.gender = :gender
              AND u.casteId = :casteId
              AND u.userId != :excludeUserId
              AND u.isActive = 'Y'
              AND u.isUser != 'ADM'
              AND ud.hobbies IS NOT NULL
              AND (
                ud.hobbies LIKE CONCAT('%', :h0, '%')
                OR (:h1 IS NOT NULL AND ud.hobbies LIKE CONCAT('%', :h1, '%'))
                OR (:h2 IS NOT NULL AND ud.hobbies LIKE CONCAT('%', :h2, '%'))
                OR (:h3 IS NOT NULL AND ud.hobbies LIKE CONCAT('%', :h3, '%'))
                OR (:h4 IS NOT NULL AND ud.hobbies LIKE CONCAT('%', :h4, '%'))
                OR (:h5 IS NOT NULL AND ud.hobbies LIKE CONCAT('%', :h5, '%'))
                OR (:h6 IS NOT NULL AND ud.hobbies LIKE CONCAT('%', :h6, '%'))
                OR (:h7 IS NOT NULL AND ud.hobbies LIKE CONCAT('%', :h7, '%'))
                OR (:h8 IS NOT NULL AND ud.hobbies LIKE CONCAT('%', :h8, '%'))
                OR (:h9 IS NOT NULL AND ud.hobbies LIKE CONCAT('%', :h9, '%'))
                OR (:h10 IS NOT NULL AND ud.hobbies LIKE CONCAT('%', :h10, '%'))
                OR (:h11 IS NOT NULL AND ud.hobbies LIKE CONCAT('%', :h11, '%'))
                OR (:h12 IS NOT NULL AND ud.hobbies LIKE CONCAT('%', :h12, '%'))
                OR (:h13 IS NOT NULL AND ud.hobbies LIKE CONCAT('%', :h13, '%'))
                OR (:h14 IS NOT NULL AND ud.hobbies LIKE CONCAT('%', :h14, '%'))
              )
            ORDER BY hasActiveBoost DESC
            LIMIT 30
            """, nativeQuery = true)
    List<FilteredUserPlanView> findInterestMatches(
            @Param("gender") String gender,
            @Param("casteId") Integer casteId,
            @Param("excludeUserId") Long excludeUserId,
            @Param("h0") String h0, @Param("h1") String h1, @Param("h2") String h2,
            @Param("h3") String h3, @Param("h4") String h4, @Param("h5") String h5,
            @Param("h6") String h6, @Param("h7") String h7, @Param("h8") String h8,
            @Param("h9") String h9, @Param("h10") String h10, @Param("h11") String h11,
            @Param("h12") String h12, @Param("h13") String h13, @Param("h14") String h14);

}
