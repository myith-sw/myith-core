package com.myith.core.domain.roadmap;

import java.time.Instant;

public class Quest {

    private Long id;
    private Long roadmapId;
    private String skillCode;       // nullable: 활동형·사용자정의
    private String axisCode;        // 항상 존재
    private int level;
    private int orderInLevel;
    private String title;
    private String completionCriteria;  // nullable: 사용자정의
    private String ncsUnitCode;         // nullable: 사용자정의
    private String guidance;            // nullable: 활동형·옛 프로필
    private QuestSource source;
    private QuestStatus status;
    private Instant completedAt;
    private long version;
    private Instant createdAt;
    private Instant updatedAt;

    private Quest() {}

    public static Quest createSkillQuest(Long roadmapId, String skillCode, String axisCode,
                                         int level, int orderInLevel, String title,
                                         String completionCriteria, String ncsUnitCode,
                                         String guidance, QuestStatus status) {
        Quest q = new Quest();
        q.roadmapId = roadmapId;
        q.skillCode = skillCode;
        q.axisCode = axisCode;
        q.level = level;
        q.orderInLevel = orderInLevel;
        q.title = title;
        q.completionCriteria = completionCriteria;
        q.ncsUnitCode = ncsUnitCode;
        q.guidance = guidance;
        q.source = QuestSource.SKILL;
        q.status = status;
        q.version = 0;
        q.createdAt = Instant.now();
        q.updatedAt = q.createdAt;
        return q;
    }

    public static Quest createActivityQuest(Long roadmapId, String axisCode,
                                            int level, int orderInLevel, String title,
                                            String completionCriteria, QuestStatus status) {
        Quest q = new Quest();
        q.roadmapId = roadmapId;
        q.skillCode = null;
        q.axisCode = axisCode;
        q.level = level;
        q.orderInLevel = orderInLevel;
        q.title = title;
        q.completionCriteria = completionCriteria;
        q.ncsUnitCode = null;
        q.source = QuestSource.ACTIVITY;
        q.status = status;
        q.version = 0;
        q.createdAt = Instant.now();
        q.updatedAt = q.createdAt;
        return q;
    }

    public static Quest createCustomQuest(Long roadmapId, String axisCode,
                                          int level, int orderInLevel, String title) {
        Quest q = new Quest();
        q.roadmapId = roadmapId;
        q.skillCode = null;
        q.axisCode = axisCode;
        q.level = level;
        q.orderInLevel = orderInLevel;
        q.title = title;
        q.completionCriteria = null;
        q.ncsUnitCode = null;
        q.source = QuestSource.CUSTOM;
        q.status = QuestStatus.OPEN;
        q.version = 0;
        q.createdAt = Instant.now();
        q.updatedAt = q.createdAt;
        return q;
    }

    // 전체 필드 복원용 (JPA 어댑터에서 사용)
    public static Quest restore(Long id, Long roadmapId, String skillCode, String axisCode,
                                int level, int orderInLevel, String title,
                                String completionCriteria, String ncsUnitCode, String guidance,
                                QuestSource source, QuestStatus status, Instant completedAt,
                                long version, Instant createdAt, Instant updatedAt) {
        Quest q = new Quest();
        q.id = id;
        q.roadmapId = roadmapId;
        q.skillCode = skillCode;
        q.axisCode = axisCode;
        q.level = level;
        q.orderInLevel = orderInLevel;
        q.title = title;
        q.completionCriteria = completionCriteria;
        q.ncsUnitCode = ncsUnitCode;
        q.guidance = guidance;
        q.source = source;
        q.status = status;
        q.completedAt = completedAt;
        q.version = version;
        q.createdAt = createdAt;
        q.updatedAt = updatedAt;
        return q;
    }

    public Long getId() { return id; }
    public Long getRoadmapId() { return roadmapId; }
    public String getSkillCode() { return skillCode; }
    public String getAxisCode() { return axisCode; }
    public int getLevel() { return level; }
    public int getOrderInLevel() { return orderInLevel; }
    public String getTitle() { return title; }
    public String getCompletionCriteria() { return completionCriteria; }
    public String getNcsUnitCode() { return ncsUnitCode; }
    public String getGuidance() { return guidance; }
    public QuestSource getSource() { return source; }
    public QuestStatus getStatus() { return status; }
    public Instant getCompletedAt() { return completedAt; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setId(Long id) { this.id = id; }
    public void setRoadmapId(Long roadmapId) { this.roadmapId = roadmapId; }
}
