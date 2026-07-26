package com.myith.core.adapter.out.persistence;

import com.myith.core.application.port.UserCompetencyReadRepository;
import com.myith.core.domain.roadmap.MasteryMerger.CompetencyEntry;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class UserCompetencyReadRepositoryAdapter implements UserCompetencyReadRepository {

    private final UserCompetencyJpaRepository jpaRepository;

    public UserCompetencyReadRepositoryAdapter(UserCompetencyJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Map<String, CompetencyEntry> findByRoadmapId(Long roadmapId) {
        return jpaRepository.findByRoadmapId(roadmapId).stream()
                .filter(e -> e.getEvidence() != null && !e.getEvidence().isBlank())
                .collect(Collectors.toMap(
                        UserCompetencyJpaEntity::getSkillCode,
                        e -> new CompetencyEntry(e.getMastery(), e.getEvidence())
                ));
    }
}
