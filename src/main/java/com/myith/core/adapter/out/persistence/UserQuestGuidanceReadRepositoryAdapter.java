package com.myith.core.adapter.out.persistence;

import com.myith.core.application.port.UserQuestGuidanceReadRepository;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class UserQuestGuidanceReadRepositoryAdapter implements UserQuestGuidanceReadRepository {

    private final UserQuestGuidanceJpaRepository jpa;

    public UserQuestGuidanceReadRepositoryAdapter(UserQuestGuidanceJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Map<String, String> findByRoadmapId(Long roadmapId) {
        return jpa.findByRoadmapId(roadmapId).stream()
                .filter(e -> e.getGuidance() != null && !e.getGuidance().isBlank())
                .collect(Collectors.toMap(
                        UserQuestGuidanceJpaEntity::getSkillCode,
                        UserQuestGuidanceJpaEntity::getGuidance,
                        (a, b) -> a));
    }
}
