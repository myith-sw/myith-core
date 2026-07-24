package com.myith.core.adapter.out.persistence;

import jakarta.persistence.*;

@Entity
@Table(name = "ncs_certification")
public class NcsCertificationJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ncs_unit_code", nullable = false, length = 50)
    private String ncsUnitCode;

    @Column(name = "cert_code", length = 50)
    private String certCode;

    @Column(name = "cert_name", nullable = false, length = 200)
    private String certName;

    @Column(name = "unit_type", length = 50)
    private String unitType;

    protected NcsCertificationJpaEntity() {}

    public String getCertName() { return certName; }
}
