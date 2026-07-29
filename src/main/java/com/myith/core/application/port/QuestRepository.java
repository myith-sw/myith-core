package com.myith.core.application.port;

import com.myith.core.domain.roadmap.Quest;
import com.myith.core.domain.roadmap.QuestStatus;

import java.util.List;
import java.util.Optional;

public interface QuestRepository {
    Quest save(Quest quest);
    List<Quest> saveAll(List<Quest> quests);
    Optional<Quest> findById(Long id);
    List<Quest> findByRoadmapId(Long roadmapId);
    void delete(Quest quest);
    void softDeleteByRoadmapIds(List<Long> roadmapIds);
    long countByRoadmapIdAndStatus(Long roadmapId, QuestStatus status);
}
