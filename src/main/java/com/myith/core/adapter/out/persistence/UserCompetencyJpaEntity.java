package com.myith.core.adapter.out.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Immutable
@Table(name = "user_competency")
@IdClass(UserCompetencyJpaEntity.UserCompetencyId.class)
public class UserCompetencyJpaEntity {

    @Id
    @Column(name = "roadmap_id")
    private Long roadmapId;

    @Id
    @Column(name = "skill_code", columnDefinition = "varchar")
    private String skillCode;

    @Column(nullable = false, precision = 3, scale = 2)
    private BigDecimal mastery;

    @Column(columnDefinition = "text")
    private String evidence;

    @Column(nullable = false, precision = 3, scale = 2)
    private BigDecimal confidence;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UserCompetencyJpaEntity() {}

    public Long getRoadmapId() { return roadmapId; }
    public String getSkillCode() { return skillCode; }
    public BigDecimal getMastery() { return mastery; }
    public String getEvidence() { return evidence; }
    public BigDecimal getConfidence() { return confidence; }
    public Instant getCreatedAt() { return createdAt; }

    public static class UserCompetencyId implements Serializable {
        private Long roadmapId;
        private String skillCode;

        public UserCompetencyId() {}

        public UserCompetencyId(Long roadmapId, String skillCode) {
            this.roadmapId = roadmapId;
            this.skillCode = skillCode;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof UserCompetencyId that)) return false;
            return roadmapId.equals(that.roadmapId) && skillCode.equals(that.skillCode);
        }

        @Override
        public int hashCode() {
            return 31 * roadmapId.hashCode() + skillCode.hashCode();
        }
    }
}
