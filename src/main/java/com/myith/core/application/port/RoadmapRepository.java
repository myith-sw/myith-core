package com.myith.core.application.port;

import com.myith.core.domain.roadmap.Roadmap;

import java.util.List;
import java.util.Optional;

public interface RoadmapRepository {
    Roadmap save(Roadmap roadmap);
    Optional<Roadmap> findById(Long id);
    List<Roadmap> findActiveByUserIdAndJobCode(Long userId, String jobCode);
}
