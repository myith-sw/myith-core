package com.myith.core.domain.diagnosis;

import java.math.BigDecimal;
import java.time.Instant;

public class UserDiagnosis {

    private Long id;
    private Long roadmapId;
    private String skillCode;
    private BigDecimal mastery;
    private Instant createdAt;

    private UserDiagnosis() {}

    public static UserDiagnosis create(Long roadmapId, String skillCode, BigDecimal mastery) {
        UserDiagnosis d = new UserDiagnosis();
        d.roadmapId = roadmapId;
        d.skillCode = skillCode;
        d.mastery = mastery;
        d.createdAt = Instant.now();
        return d;
    }

    public Long getId() { return id; }
    public Long getRoadmapId() { return roadmapId; }
    public String getSkillCode() { return skillCode; }
    public BigDecimal getMastery() { return mastery; }
    public Instant getCreatedAt() { return createdAt; }

    public void setId(Long id) { this.id = id; }
}
