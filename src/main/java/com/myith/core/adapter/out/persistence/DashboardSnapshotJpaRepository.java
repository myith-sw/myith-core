package com.myith.core.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DashboardSnapshotJpaRepository extends JpaRepository<DashboardSnapshotJpaEntity, Long> {

    Optional<DashboardSnapshotJpaEntity> findByRoadmapId(Long roadmapId);
}
