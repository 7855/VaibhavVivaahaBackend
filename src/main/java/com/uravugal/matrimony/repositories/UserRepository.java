package com.uravugal.matrimony.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.uravugal.matrimony.enums.ActiveStatus;
import com.uravugal.matrimony.enums.EmploymentType;
import com.uravugal.matrimony.enums.Gender;
import com.uravugal.matrimony.models.UserEntity;

@Repository
public interface UserRepository extends JpaRepository<UserEntity,Long>{


    List<UserEntity> findAllByCasteIdAndIsActive(Integer caste,ActiveStatus active);

    List<UserEntity> findAllByCasteIdAndLocationAndIsActive(Integer casteId,String location, ActiveStatus active);

    List<UserEntity> findTop30ByCasteIdOrderByCreatedAtDesc(Integer casteId);

    @Query(value = "SELECT u FROM UserEntity u WHERE u.casteId = :casteId AND u.isActive = :active ORDER BY RAND()", nativeQuery = false)
    List<UserEntity> findAllByCasteIdAndIsActiveOrderByRandom(@Param("casteId") Integer caste, @Param("active") ActiveStatus active);

    List<UserEntity> findAllByIsActive(ActiveStatus active);

    @Query(value = "SELECT u FROM UserEntity u WHERE u.isActive = :active ORDER BY RAND()", nativeQuery = false)
    List<UserEntity> findAllByIsActiveOrderByRandom(@Param("active") ActiveStatus active);

    @Query(value = "SELECT u FROM UserEntity u WHERE u.gender = :gender AND u.casteId = :casteId AND u.isActive = :active ORDER BY RAND()", nativeQuery = false)
    List<UserEntity> findAllByGenderAndCasteIdAndIsActiveOrderByRandom(
            @Param("gender") Gender gender,
            @Param("casteId") Integer casteId,
            @Param("active") ActiveStatus active);


            @Query(value = """
                SELECT u.* 
                FROM users u
                JOIN user_details ud ON u.userId = ud.userId
                WHERE u.isActive = 'Y'
                  AND (:gender IS NULL OR u.gender = :gender)
                  AND (:casteId IS NULL OR u.casteId = :casteId)
                  AND (:minAge IS NULL OR CAST(u.age AS UNSIGNED) >= :minAge)
                  AND (:maxAge IS NULL OR CAST(u.age AS UNSIGNED) <= :maxAge)
                  AND (:location IS NULL OR u.location = :location)
                  AND (:minAnnualIncome IS NULL OR CAST(ud.annualIncome AS UNSIGNED) >= :minAnnualIncome)
                  AND (:maxAnnualIncome IS NULL OR CAST(ud.annualIncome AS UNSIGNED) <= :maxAnnualIncome)
                  AND (:occupation IS NULL OR ud.occupation = :occupation)
                  AND (:employedAt IS NULL OR ud.employedAt = :employedAt)
                  AND (:star IS NULL OR JSON_UNQUOTE(JSON_EXTRACT(ud.astroInfo, '$[0].star')) = :star)
                  AND (:dosham IS NULL OR JSON_UNQUOTE(JSON_EXTRACT(ud.astroInfo, '$[0].dosham')) = :dosham)
                  AND (:profileImageStatus IS NULL OR :profileImageStatus = 'N' OR (:profileImageStatus = 'Y' AND u.profileImage IS NOT NULL))
AND (:profilesWithHoroscope IS NULL 
     OR :profilesWithHoroscope = 'N' 
     OR (:profilesWithHoroscope = 'Y' AND ud.horoscope IS NOT NULL))
                ORDER BY RAND()
            """, nativeQuery = true)
            List<UserEntity> advanceFilter(
                    @Param("gender") String gender,
                    @Param("casteId") Integer casteId,
                    @Param("minAge") Integer minAge,
                    @Param("maxAge") Integer maxAge,
                    @Param("minAnnualIncome") Integer minAnnualIncome,
                    @Param("maxAnnualIncome") Integer maxAnnualIncome,
                    @Param("occupation") String occupation,
                    @Param("location") String location,
                    @Param("employedAt") String employedAt,
                    @Param("profileImageStatus") String profileImageStatus,
                    @Param("star") String star,
                    @Param("dosham") String dosham,
                    @Param("profilesWithHoroscope") String profilesWithHoroscope
            );
            
            
            
            
            
    UserEntity findByMobile(String mobile);

    Optional<UserEntity> findByUserId(Long id);

    List<UserEntity> findAllByCasteIdAndGenderAndIsActive(Integer casteId, Gender gender, ActiveStatus y);

    List<UserEntity> findTop30ByCasteIdAndGenderAndIsActiveOrderByCreatedAtDesc(Integer casteId, Gender gender,
            ActiveStatus y);

    List<UserEntity> findAllByCasteIdAndGenderAndLocationAndIsActive(Integer casteId, Gender gender, String location,
            ActiveStatus y);

    @Query(value = "SELECT memberId FROM users ORDER BY memberId DESC LIMIT 1", nativeQuery = true)
    String findLastMemberId();


    @Query("""
        SELECT u FROM UserEntity u
        JOIN u.userDetail ud
        WHERE
            u.isActive = :active AND
            (:gender IS NULL OR u.gender = :gender) AND
            (:casteId IS NULL OR u.casteId = :casteId) AND
            (:minAge IS NULL OR u.age >= :minAge) AND
            (:maxAge IS NULL OR u.age <= :maxAge) AND
            (:location IS NULL OR u.location = :location)
        AND (
    :profileImageStatus IS NULL OR
    :profileImageStatus = 'N' OR
    (:profileImageStatus = 'Y' AND u.profileImage IS NOT NULL)
)

        ORDER BY function('RAND')
    """)
    List<UserEntity> normalFilter(
            @Param("active") ActiveStatus active,
            @Param("gender") Gender gender,
            @Param("casteId") Integer casteId,
            @Param("minAge") Integer minAge,
            @Param("maxAge") Integer maxAge,
            @Param("location") String location,
            @Param("profileImageStatus") String profileImageStatus
    );

    Optional<UserEntity> findByMemberIdAndGenderAndCasteId(String memberId, Gender gender, Integer casteId);
     
    }
