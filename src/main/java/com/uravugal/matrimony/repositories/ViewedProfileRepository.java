package com.uravugal.matrimony.repositories;

import java.util.List;
import com.uravugal.matrimony.models.ViewedProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ViewedProfileRepository extends JpaRepository<ViewedProfile, Long> {
    boolean existsByUserIdValueAndViewedBy(Long userIdValue, Long viewedBy);
    List<ViewedProfile> findByUserIdValue(Long userIdValue);
}
