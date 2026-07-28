package com.myith.core.adapter.out.persistence;

import com.myith.core.domain.roadmap.Quest;
import com.myith.core.domain.roadmap.QuestSource;
import com.myith.core.domain.roadmap.QuestStatus;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "quest")
public class QuestJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "roadmap_id", nullable = false)
    private Long roadmapId;

    @Column(name = "skill_code")
    private String skillCode;

    @Column(name = "axis_code", nullable = false)
    private String axisCode;

    @Column(name = "level", nullable = false)
    private int level;

    @Column(name = "order_in_level", nullable = false)
    private int orderInLevel;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "completion_criteria")
    private String completionCriteria;

    @Column(name = "ncs_unit_code")
    private String ncsUnitCode;

    @Column(name = "guidance")
    private String guidance;

    @Column(name = "source", nullable = false)
    private String source;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected QuestJpaEntity() {}

    public static QuestJpaEntity fromDomain(Quest quest) {
        QuestJpaEntity entity = new QuestJpaEntity();
        entity.id = quest.getId();
        entity.roadmapId = quest.getRoadmapId();
        entity.skillCode = quest.getSkillCode();
        entity.axisCode = quest.getAxisCode();
        entity.level = quest.getLevel();
        entity.orderInLevel = quest.getOrderInLevel();
        entity.title = quest.getTitle();
        entity.completionCriteria = quest.getCompletionCriteria();
        entity.ncsUnitCode = quest.getNcsUnitCode();
        entity.guidance = quest.getGuidance();
        entity.source = quest.getSource().name();
        entity.status = quest.getStatus().name();
        entity.completedAt = quest.getCompletedAt();
        entity.version = quest.getVersion();
        entity.createdAt = quest.getCreatedAt();
        entity.updatedAt = quest.getUpdatedAt();
        return entity;
    }

    public Quest toDomain() {
        return Quest.restore(
                id, roadmapId, skillCode, axisCode,
                level, orderInLevel, title,
                completionCriteria, ncsUnitCode, guidance,
                QuestSource.valueOf(source),
                QuestStatus.valueOf(status),
                completedAt, version, createdAt, updatedAt
        );
    }

    public void setDeletedAt(java.time.Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
