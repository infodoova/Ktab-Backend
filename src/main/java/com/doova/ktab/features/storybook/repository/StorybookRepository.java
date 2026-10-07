package com.doova.ktab.features.storybook.repository;

import com.doova.ktab.features.storybook.model.Storybook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface StorybookRepository extends JpaRepository<Storybook, Long> {
    Optional<Storybook> findByIdAndOwner_Id(Long id, Long ownerId);

    @Query("select b.id from Storybook b where b.status = :status")
    List<Long> findIdsByStatus(@Param("status") com.doova.ktab.features.storybook.enums.StorybookStatus status);

    /** Serializes book-level status advancement when several page jobs finish at once (sub-plan 05). */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Storybook b where b.id = :id")
    Optional<Storybook> findByIdForUpdate(@Param("id") Long id);

    @Query("select b from Storybook b join fetch b.owner where b.id = :id")
    Optional<Storybook> findByIdWithOwner(@Param("id") Long id);

    List<Storybook> findByOwner_IdOrderByCreatedAtDesc(Long ownerId);

    long countByOwner_IdAndCreatedAtAfter(Long ownerId, LocalDateTime since);

    List<Storybook> findByChildProfile_Id(Long childProfileId);

    @Modifying
    @Query("update Storybook b set b.totalCostUsd = b.totalCostUsd + :cost where b.id = :id")
    int addCost(@Param("id") Long id, @Param("cost") BigDecimal cost);
}
