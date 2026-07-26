package com.myith.core.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DiagnosisJpaRepository extends JpaRepository<DiagnosisJpaEntity, Long> {
    List<DiagnosisJpaEntity> findByRoadmapId(Long roadmapId);

    @Modifying
    @Query("DELETE FROM DiagnosisJpaEntity d WHERE d.roadmapId IN :roadmapIds")
    void deleteByRoadmapIds(@Param("roadmapIds") List<Long> roadmapIds);
}
