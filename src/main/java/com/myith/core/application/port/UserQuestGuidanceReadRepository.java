package com.myith.core.application.port;

import java.util.Map;

/**
 * Worker 소유 user_quest_guidance 테이블 읽기 전용 포트 (C-3).
 * Alembic 0006으로 생성된 테이블이므로 Flyway DDL을 만들지 않는다.
 */
public interface UserQuestGuidanceReadRepository {

    /**
     * @return skillCode → guidance 문구. 비어있거나 null인 행은 제외.
     */
    Map<String, String> findByRoadmapId(Long roadmapId);
}
