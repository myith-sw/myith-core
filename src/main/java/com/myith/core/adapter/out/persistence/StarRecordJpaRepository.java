package com.myith.core.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StarRecordJpaRepository extends JpaRepository<StarRecordJpaEntity, Long> {

    Optional<StarRecordJpaEntity> findByQuestIdAndDeletedAtIsNull(Long questId);

    @Query("""
        SELECT s FROM StarRecordJpaEntity s
        WHERE s.userId = :userId AND s.deletedAt IS NULL
        AND (:cursor IS NULL OR s.id < :cursor)
        AND (:completeness IS NULL OR s.completeness = :completeness)
        ORDER BY s.id DESC
        """)
    List<StarRecordJpaEntity> findByCursor(
            @Param("userId") Long userId,
            @Param("cursor") Long cursor,
            @Param("completeness") String completeness,
            org.springframework.data.domain.Pageable pageable);
}
