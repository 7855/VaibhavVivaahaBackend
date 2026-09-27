package com.uravugal.matrimony.repositories;

import com.uravugal.matrimony.enums.ActiveStatus;
import com.uravugal.matrimony.models.SubCasteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SubCasteRepository extends JpaRepository<SubCasteEntity, Long> {

    /**
     * Ordered alphabetically but with "Others" forced to the BOTTOM of the list. Plain
     * alphabetical ordering would bury the catch-all option in the middle of the real
     * subcastes (e.g. between "Mazhavarayar" and "Padayachi"), where it reads as just another
     * community name rather than the "I'm not sure / not listed" escape hatch it is.
     */
    @Query("SELECT s FROM SubCasteEntity s WHERE s.casteId = :casteId AND s.isActive = :isActive "
            + "ORDER BY CASE WHEN s.subcasteName = 'Others' THEN 1 ELSE 0 END ASC, s.subcasteName ASC")
    List<SubCasteEntity> findByCasteIdAndIsActiveOrderBySubcasteNameAsc(
            @Param("casteId") Long casteId, @Param("isActive") ActiveStatus isActive);

    /** Same "Others"-last rule, across every caste. */
    @Query("SELECT s FROM SubCasteEntity s WHERE s.isActive = :isActive "
            + "ORDER BY s.casteId ASC, CASE WHEN s.subcasteName = 'Others' THEN 1 ELSE 0 END ASC, s.subcasteName ASC")
    List<SubCasteEntity> findByIsActiveOrderByCasteIdAscSubcasteNameAsc(
            @Param("isActive") ActiveStatus isActive);

    /**
     * How many subcastes a caste actually has. Used to decide whether subcaste is REQUIRED at
     * registration: castes with a real list (Vanniyar, Naidu, ...) require it, while a
     * caste-level option like FREE CASTE BAR ("any caste is fine") has none and must not block
     * signup. Enum spelled out so callers don't need to import ActiveStatus.
     */
    @Query("SELECT COUNT(s) FROM SubCasteEntity s WHERE s.casteId = :casteId "
            + "AND s.isActive = com.uravugal.matrimony.enums.ActiveStatus.Y")
    long countActiveByCasteId(@Param("casteId") Long casteId);
}
