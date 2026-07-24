package com.myith.core.adapter.out.persistence;

import jakarta.persistence.*;

@Entity
@Table(name = "job")
public class JobJpaEntity {

    @Id
    @Column(name = "job_code", length = 50)
    private String jobCode;

    @Column(name = "job_name", nullable = false, length = 100)
    private String jobName;

    @Column(name = "category_code", nullable = false, length = 50)
    private String categoryCode;

    @Column(name = "category_name", nullable = false, length = 100)
    private String categoryName;

    @Column(columnDefinition = "TEXT")
    private String tagline;

    protected JobJpaEntity() {}

    public String getJobCode() { return jobCode; }
    public String getJobName() { return jobName; }
    public String getCategoryCode() { return categoryCode; }
    public String getCategoryName() { return categoryName; }
    public String getTagline() { return tagline; }
}
