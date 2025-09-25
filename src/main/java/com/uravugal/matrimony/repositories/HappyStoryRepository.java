package com.uravugal.matrimony.repositories;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.uravugal.matrimony.models.HappyStoryEntity;
import com.uravugal.matrimony.enums.ActiveStatus;

public interface HappyStoryRepository extends JpaRepository<HappyStoryEntity, String> {
    List<HappyStoryEntity> findByIsActive(ActiveStatus isActive);
    HappyStoryEntity findByHappystoryId(String happystoryId);
}
