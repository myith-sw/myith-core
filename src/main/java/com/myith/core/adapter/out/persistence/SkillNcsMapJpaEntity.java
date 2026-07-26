package com.myith.core.adapter.out.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

import java.io.Serializable;

@Entity
@Immutable
@Table(name = "skill_ncs_map")
@IdClass(SkillNcsMapJpaEntity.SkillNcsMapId.class)
public class SkillNcsMapJpaEntity {

    @Id
    @Column(name = "skill_code", columnDefinition = "varchar")
    private String skillCode;

    @Id
    @Column(name = "ncs_unit_code", columnDefinition = "varchar")
    private String ncsUnitCode;

    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary;

    protected SkillNcsMapJpaEntity() {}

    public String getSkillCode() { return skillCode; }
    public String getNcsUnitCode() { return ncsUnitCode; }
    public boolean isPrimary() { return isPrimary; }

    public static class SkillNcsMapId implements Serializable {
        private String skillCode;
        private String ncsUnitCode;

        public SkillNcsMapId() {}

        public SkillNcsMapId(String skillCode, String ncsUnitCode) {
            this.skillCode = skillCode;
            this.ncsUnitCode = ncsUnitCode;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SkillNcsMapId that)) return false;
            return skillCode.equals(that.skillCode) && ncsUnitCode.equals(that.ncsUnitCode);
        }

        @Override
        public int hashCode() {
            return 31 * skillCode.hashCode() + ncsUnitCode.hashCode();
        }
    }
}
