package com.myith.core.adapter.out.persistence;

import com.myith.core.domain.roadmap.GenerationState;
import com.myith.core.domain.roadmap.Roadmap;
import com.myith.core.domain.roadmap.RoadmapStatus;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "roadmap")
public class RoadmapJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "job_code", nullable = false)
    private String jobCode;

    @Column(name = "profile_version", nullable = false)
    private int profileVersion;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "generation_state", nullable = false)
    private String generationState;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected RoadmapJpaEntity() {}

    public static RoadmapJpaEntity fromDomain(Roadmap roadmap) {
        RoadmapJpaEntity entity = new RoadmapJpaEntity();
        entity.id = roadmap.getId();
        entity.userId = roadmap.getUserId();
        entity.jobCode = roadmap.getJobCode();
        entity.profileVersion = roadmap.getProfileVersion();
        entity.status = roadmap.getStatus().name();
        entity.generationState = roadmap.getGenerationState().name();
        entity.retryCount = roadmap.getRetryCount();
        entity.createdAt = roadmap.getCreatedAt();
        entity.updatedAt = roadmap.getUpdatedAt();
        entity.archivedAt = roadmap.getArchivedAt();
        return entity;
    }

    public Roadmap toDomain() {
        return Roadmap.restore(
                id, userId, jobCode, profileVersion,
                RoadmapStatus.valueOf(status),
                GenerationState.valueOf(generationState),
                retryCount, createdAt, updatedAt, archivedAt
        );
    }
}
