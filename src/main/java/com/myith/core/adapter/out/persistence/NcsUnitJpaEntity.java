package com.myith.core.adapter.out.persistence;

import jakarta.persistence.*;

@Entity
@org.hibernate.annotations.Immutable
@Table(name = "ncs_unit")
public class NcsUnitJpaEntity {

    @Id
    @Column(columnDefinition = "varchar")
    private String code;

    @Column(nullable = false, columnDefinition = "varchar")
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private Integer level;

    @Column(name = "major_name", columnDefinition = "varchar")
    private String majorName;

    @Column(name = "middle_name", columnDefinition = "varchar")
    private String middleName;

    @Column(name = "minor_name", columnDefinition = "varchar")
    private String minorName;

    @Column(name = "detail_name", columnDefinition = "varchar")
    private String detailName;

    @Column(name = "is_verified", nullable = false)
    private boolean isVerified;

    protected NcsUnitJpaEntity() {}

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Integer getLevel() { return level; }
    public String getMajorName() { return majorName; }
    public String getMiddleName() { return middleName; }
    public String getMinorName() { return minorName; }
    public String getDetailName() { return detailName; }
    public boolean isVerified() { return isVerified; }
}
