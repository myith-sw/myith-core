package com.myith.core.application.port;

import com.myith.core.domain.roadmap.MasteryMerger.CompetencyEntry;

import java.util.Map;

public interface UserCompetencyReadRepository {

    /**
     * roadmapId에 해당하는 user_competency를 조회한다.
     * evidence가 null이거나 공백인 행은 제외한다 (D-2 병합 규칙).
     * @return skillCode → CompetencyEntry 맵. 없으면 빈 맵.
     */
    Map<String, CompetencyEntry> findByRoadmapId(Long roadmapId);
}
