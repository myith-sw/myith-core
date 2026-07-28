package com.myith.core.adapter.out.persistence;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Worker 소유 테이블. Core는 읽기 전용 (C-3).
 * DDL은 Worker Alembic 0006이 관리한다.
 */
@Entity
@Table(name = "user_quest_guidance")
@IdClass(UserQuestGuidanceJpaEntity.PK.class)
public class UserQuestGuidanceJpaEntity {

    @Id
    @Column(name = "roadmap_id")
    private Long roadmapId;

    @Id
    @Column(name = "skill_code")
    private String skillCode;

    @Column(name = "guidance")
    private String guidance;

    @Column(name = "tier")
    private String tier;

    @Column(name = "created_at")
    private Instant createdAt;

    public String getSkillCode() { return skillCode; }
    public String getGuidance() { return guidance; }

    public static class PK implements java.io.Serializable {
        private Long roadmapId;
        private String skillCode;

        public PK() {}
        public PK(Long roadmapId, String skillCode) {
            this.roadmapId = roadmapId;
            this.skillCode = skillCode;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PK pk)) return false;
            return java.util.Objects.equals(roadmapId, pk.roadmapId)
                    && java.util.Objects.equals(skillCode, pk.skillCode);
        }
        @Override public int hashCode() { return java.util.Objects.hash(roadmapId, skillCode); }
    }
}
