package com.myith.core.domain.roadmap;

import java.time.Instant;

public class Roadmap {

    private Long id;
    private Long userId;
    private String jobCode;
    private int profileVersion;
    private RoadmapStatus status;
    private GenerationState generationState;
    private int retryCount;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant archivedAt;

    private Roadmap() {}

    public static Roadmap create(Long userId, String jobCode, int profileVersion, GenerationState initialState) {
        Roadmap r = new Roadmap();
        r.userId = userId;
        r.jobCode = jobCode;
        r.profileVersion = profileVersion;
        r.status = RoadmapStatus.ACTIVE;
        r.generationState = initialState;
        r.retryCount = 0;
        r.createdAt = Instant.now();
        r.updatedAt = r.createdAt;
        return r;
    }

    public static Roadmap restore(Long id, Long userId, String jobCode, int profileVersion,
                                  RoadmapStatus status, GenerationState generationState,
                                  int retryCount, Instant createdAt, Instant updatedAt,
                                  Instant archivedAt) {
        Roadmap r = new Roadmap();
        r.id = id;
        r.userId = userId;
        r.jobCode = jobCode;
        r.profileVersion = profileVersion;
        r.status = status;
        r.generationState = generationState;
        r.retryCount = retryCount;
        r.createdAt = createdAt;
        r.updatedAt = updatedAt;
        r.archivedAt = archivedAt;
        return r;
    }

    public void archive() {
        this.status = RoadmapStatus.ARCHIVED;
        this.archivedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void markReady() {
        this.generationState = GenerationState.READY;
        this.updatedAt = Instant.now();
    }

    public void markAnalyzing() {
        this.generationState = GenerationState.ANALYZING;
        this.updatedAt = Instant.now();
    }

    public void markFailed() {
        this.generationState = GenerationState.FAILED;
        this.updatedAt = Instant.now();
    }

    public void incrementRetry() {
        this.retryCount++;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getJobCode() { return jobCode; }
    public int getProfileVersion() { return profileVersion; }
    public RoadmapStatus getStatus() { return status; }
    public GenerationState getGenerationState() { return generationState; }
    public int getRetryCount() { return retryCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getArchivedAt() { return archivedAt; }

    public void setId(Long id) { this.id = id; }
}
