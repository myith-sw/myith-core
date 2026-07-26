package com.myith.core.adapter.out.persistence;

import jakarta.persistence.*;

@Entity
@org.hibernate.annotations.Immutable
@Table(name = "job")
public class JobJpaEntity {

    @Id
    @Column(name = "job_code", columnDefinition = "varchar")
    private String jobCode;

    @Column(name = "job_name", nullable = false, columnDefinition = "varchar")
    private String jobName;

    @Column(name = "category_code", columnDefinition = "varchar")
    private String categoryCode;

    @Column(name = "category_name", columnDefinition = "varchar")
    private String categoryName;

    @Column(columnDefinition = "text")
    private String tagline;

    @Column(name = "ncs_detail_code", columnDefinition = "varchar")
    private String ncsDetailCode;

    protected JobJpaEntity() {}

    public String getJobCode() { return jobCode; }
    public String getJobName() { return jobName; }
    public String getCategoryCode() { return categoryCode; }
    public String getCategoryName() { return categoryName; }
    public String getTagline() { return tagline; }
    public String getNcsDetailCode() { return ncsDetailCode; }
}
