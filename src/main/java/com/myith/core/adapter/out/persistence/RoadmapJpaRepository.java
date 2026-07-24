package com.myith.core.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoadmapJpaRepository extends JpaRepository<RoadmapJpaEntity, Long> {

    Optional<RoadmapJpaEntity> findByIdAndDeletedAtIsNull(Long id);

    List<RoadmapJpaEntity> findByUserIdAndStatusAndDeletedAtIsNull(Long userId, String status);
}
