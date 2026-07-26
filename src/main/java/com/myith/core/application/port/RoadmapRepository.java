package com.myith.core.application.port;

import com.myith.core.domain.roadmap.Roadmap;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RoadmapRepository {
    Roadmap save(Roadmap roadmap);
    Optional<Roadmap> findById(Long id);
    List<Roadmap> findActiveByUserIdAndJobCode(Long userId, String jobCode);
    List<Roadmap> findActiveByUserId(Long userId);
    List<Roadmap> findStuckAnalyzing(Instant cutoff);
    List<Roadmap> findByUserId(Long userId);
    void softDeleteByUserId(Long userId);
}
