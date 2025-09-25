package com.uravugal.matrimony.repositories;

import com.uravugal.matrimony.enums.ActiveStatus;
import com.uravugal.matrimony.models.CasteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CasteRepository extends JpaRepository<CasteEntity, Integer> {
    List<CasteEntity> findByIsActive(ActiveStatus isActive);
}
