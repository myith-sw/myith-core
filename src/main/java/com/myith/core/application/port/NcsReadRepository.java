package com.myith.core.application.port;

import java.util.List;
import java.util.Optional;

public interface NcsReadRepository {

    Optional<NcsUnitData> findUnitByCode(String code);

    List<CertificationData> findCertificationsByUnitCode(String unitCode);

    record NcsUnitData(String code, String name, String description) {}

    record CertificationData(String certName) {}
}
