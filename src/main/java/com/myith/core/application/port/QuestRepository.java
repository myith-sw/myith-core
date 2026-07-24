package com.myith.core.application.port;

import com.myith.core.domain.roadmap.Quest;

import java.util.List;

public interface QuestRepository {
    List<Quest> saveAll(List<Quest> quests);
    List<Quest> findByRoadmapId(Long roadmapId);
}
