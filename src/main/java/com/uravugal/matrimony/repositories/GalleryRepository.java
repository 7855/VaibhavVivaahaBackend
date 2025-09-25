package com.uravugal.matrimony.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.uravugal.matrimony.enums.ActiveStatus;
import com.uravugal.matrimony.models.GalleryEntity;

@Repository
public interface GalleryRepository extends JpaRepository<GalleryEntity, Long> {
    
    List<GalleryEntity> findByUserIdAndIsActive(Long userId, ActiveStatus activeStatus);

    GalleryEntity findByGalleryIdAndIsActive(Long galleryId, ActiveStatus activeStatus);
}
