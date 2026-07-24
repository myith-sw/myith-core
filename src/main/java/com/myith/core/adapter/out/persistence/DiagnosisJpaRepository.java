package com.myith.core.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DiagnosisJpaRepository extends JpaRepository<DiagnosisJpaEntity, Long> {
    List<DiagnosisJpaEntity> findByRoadmapId(Long roadmapId);
}
