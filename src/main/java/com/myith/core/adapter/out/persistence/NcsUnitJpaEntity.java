package com.myith.core.adapter.out.persistence;

import jakarta.persistence.*;

@Entity
@Table(name = "ncs_unit")
public class NcsUnitJpaEntity {

    @Id
    @Column(length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    private Integer level;

    protected NcsUnitJpaEntity() {}

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Integer getLevel() { return level; }
}
