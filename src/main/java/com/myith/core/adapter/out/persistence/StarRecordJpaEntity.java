package com.myith.core.adapter.out.persistence;

import com.myith.core.domain.star.StarRecord;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "star_record")
public class StarRecordJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "quest_id", nullable = false)
    private Long questId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(columnDefinition = "TEXT")
    private String situation;

    @Column(columnDefinition = "TEXT")
    private String task;

    @Column(columnDefinition = "TEXT")
    private String action;

    @Column(columnDefinition = "TEXT")
    private String result;

    @Column(length = 20)
    private String completeness;

    @Column(columnDefinition = "VARCHAR[]")
    private String tags;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected StarRecordJpaEntity() {}

    public static StarRecordJpaEntity fromDomain(StarRecord record) {
        StarRecordJpaEntity e = new StarRecordJpaEntity();
        e.id = record.getId();
        e.questId = record.getQuestId();
        e.userId = record.getUserId();
        e.situation = record.getSituation();
        e.task = record.getTask();
        e.action = record.getAction();
        e.result = record.getResult();
        e.completeness = record.getCompleteness();
        e.tags = record.getTags() != null ? String.join(",", record.getTags()) : null;
        e.createdAt = record.getCreatedAt();
        e.updatedAt = record.getUpdatedAt();
        return e;
    }

    public StarRecord toDomain() {
        List<String> tagList = tags != null && !tags.isBlank()
                ? Arrays.asList(tags.split(",")) : null;
        return StarRecord.restore(id, questId, userId, situation, task, action, result,
                completeness, tagList, createdAt, updatedAt);
    }
}
