package com.myith.core.adapter.out.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.time.Instant;

@Entity
@org.hibernate.annotations.Immutable
@Table(name = "job_profile")
@IdClass(JobProfileJpaEntity.JobProfileId.class)
public class JobProfileJpaEntity {

    @Id
    @Column(name = "job_code", length = 50)
    private String jobCode;

    @Id
    private int version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String axes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String skills;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String levels;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String prerequisites;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String questions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "quest_templates", columnDefinition = "jsonb")
    private String questTemplates;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "activity_quests", columnDefinition = "jsonb")
    private String activityQuests;

    @Column(name = "built_at")
    private Instant builtAt;

    protected JobProfileJpaEntity() {}

    public String getJobCode() { return jobCode; }
    public int getVersion() { return version; }
    public String getAxes() { return axes; }
    public String getSkills() { return skills; }
    public String getLevels() { return levels; }
    public String getPrerequisites() { return prerequisites; }
    public String getQuestions() { return questions; }
    public String getQuestTemplates() { return questTemplates; }
    public String getActivityQuests() { return activityQuests; }

    public static class JobProfileId implements Serializable {
        private String jobCode;
        private int version;

        public JobProfileId() {}
        public JobProfileId(String jobCode, int version) {
            this.jobCode = jobCode;
            this.version = version;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof JobProfileId that)) return false;
            return version == that.version && jobCode.equals(that.jobCode);
        }

        @Override
        public int hashCode() {
            return 31 * jobCode.hashCode() + version;
        }
    }
}
