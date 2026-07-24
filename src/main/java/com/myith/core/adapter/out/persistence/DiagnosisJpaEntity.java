package com.myith.core.adapter.out.persistence;

import com.myith.core.domain.diagnosis.UserDiagnosis;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "user_diagnosis")
public class DiagnosisJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "roadmap_id", nullable = false)
    private Long roadmapId;

    @Column(name = "skill_code", nullable = false)
    private String skillCode;

    @Column(name = "mastery", nullable = false)
    private BigDecimal mastery;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DiagnosisJpaEntity() {}

    public static DiagnosisJpaEntity fromDomain(UserDiagnosis diagnosis) {
        DiagnosisJpaEntity entity = new DiagnosisJpaEntity();
        entity.id = diagnosis.getId();
        entity.roadmapId = diagnosis.getRoadmapId();
        entity.skillCode = diagnosis.getSkillCode();
        entity.mastery = diagnosis.getMastery();
        entity.createdAt = diagnosis.getCreatedAt();
        return entity;
    }

    public UserDiagnosis toDomain() {
        UserDiagnosis d = UserDiagnosis.create(roadmapId, skillCode, mastery);
        d.setId(id);
        return d;
    }
}
