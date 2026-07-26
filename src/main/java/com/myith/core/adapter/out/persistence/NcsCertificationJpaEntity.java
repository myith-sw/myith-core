package com.myith.core.adapter.out.persistence;

import jakarta.persistence.*;

import java.io.Serializable;

@Entity
@org.hibernate.annotations.Immutable
@Table(name = "ncs_certification")
@IdClass(NcsCertificationJpaEntity.NcsCertificationId.class)
public class NcsCertificationJpaEntity {

    @Id
    @Column(name = "ncs_unit_code", columnDefinition = "varchar")
    private String ncsUnitCode;

    @Id
    @Column(name = "cert_code", columnDefinition = "varchar")
    private String certCode;

    @Column(name = "cert_name", nullable = false, columnDefinition = "varchar")
    private String certName;

    @Column(name = "unit_type", columnDefinition = "varchar")
    private String unitType;

    protected NcsCertificationJpaEntity() {}

    public String getNcsUnitCode() { return ncsUnitCode; }
    public String getCertCode() { return certCode; }
    public String getCertName() { return certName; }
    public String getUnitType() { return unitType; }

    public static class NcsCertificationId implements Serializable {
        private String ncsUnitCode;
        private String certCode;

        public NcsCertificationId() {}

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof NcsCertificationId that)) return false;
            return ncsUnitCode.equals(that.ncsUnitCode) && certCode.equals(that.certCode);
        }

        @Override
        public int hashCode() {
            return 31 * ncsUnitCode.hashCode() + certCode.hashCode();
        }
    }
}
