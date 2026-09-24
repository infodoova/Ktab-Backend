package com.doova.ktab.features.storybook.repository;

import com.doova.ktab.features.storybook.model.ChildProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChildProfileRepository extends JpaRepository<ChildProfile, Long> {
    Optional<ChildProfile> findByIdAndOwner_Id(Long id, Long ownerId);
    List<ChildProfile> findByOwner_IdOrderByCreatedAtDesc(Long ownerId);
}
