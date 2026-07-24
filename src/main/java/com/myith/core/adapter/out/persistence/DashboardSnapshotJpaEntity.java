package com.myith.core.adapter.out.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "dashboard_snapshot")
public class DashboardSnapshotJpaEntity {

    @Id
    @Column(name = "roadmap_id")
    private Long roadmapId;

    @Column(name = "completion_rate", nullable = false)
    private BigDecimal completionRate;

    @Column(name = "stage", nullable = false)
    private String stage;

    @Column(name = "max_stage", nullable = false)
    private String maxStage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "radar", columnDefinition = "jsonb")
    private String radar;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    @Column(name = "version", nullable = false)
    private long version;

    protected DashboardSnapshotJpaEntity() {}

    public Long getRoadmapId() { return roadmapId; }
    public BigDecimal getCompletionRate() { return completionRate; }
    public String getStage() { return stage; }
    public String getMaxStage() { return maxStage; }
    public String getRadar() { return radar; }
    public Instant getComputedAt() { return computedAt; }
    public long getVersion() { return version; }

    public void setRoadmapId(Long roadmapId) { this.roadmapId = roadmapId; }
    public void setCompletionRate(BigDecimal completionRate) { this.completionRate = completionRate; }
    public void setStage(String stage) { this.stage = stage; }
    public void setMaxStage(String maxStage) { this.maxStage = maxStage; }
    public void setRadar(String radar) { this.radar = radar; }
    public void setComputedAt(Instant computedAt) { this.computedAt = computedAt; }
    public void setVersion(long version) { this.version = version; }
}
