package com.myith.core.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NcsCertificationJpaRepository extends JpaRepository<NcsCertificationJpaEntity, NcsCertificationJpaEntity.NcsCertificationId> {
    List<NcsCertificationJpaEntity> findByNcsUnitCode(String ncsUnitCode);
}
