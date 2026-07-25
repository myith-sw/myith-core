package com.myith.core.adapter.out.persistence;

import com.myith.core.application.port.NcsReadRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class NcsReadRepositoryAdapter implements NcsReadRepository {

    private final NcsUnitJpaRepository unitRepo;
    private final NcsCertificationJpaRepository certRepo;

    public NcsReadRepositoryAdapter(NcsUnitJpaRepository unitRepo,
                                    NcsCertificationJpaRepository certRepo) {
        this.unitRepo = unitRepo;
        this.certRepo = certRepo;
    }

    @Override
    public Optional<NcsUnitData> findUnitByCode(String code) {
        return unitRepo.findById(code)
                .map(e -> new NcsUnitData(e.getCode(), e.getName(), e.getDescription()));
    }

    @Override
    public List<CertificationData> findCertificationsByUnitCode(String unitCode) {
        return certRepo.findByNcsUnitCode(unitCode).stream()
                .map(e -> new CertificationData(e.getCertName()))
                .toList();
    }
}
