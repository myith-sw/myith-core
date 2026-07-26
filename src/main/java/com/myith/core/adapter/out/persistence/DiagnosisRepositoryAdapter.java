package com.myith.core.adapter.out.persistence;

import com.myith.core.application.port.DiagnosisRepository;
import com.myith.core.domain.diagnosis.UserDiagnosis;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class DiagnosisRepositoryAdapter implements DiagnosisRepository {

    private final DiagnosisJpaRepository jpaRepository;

    public DiagnosisRepositoryAdapter(DiagnosisJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<UserDiagnosis> saveAll(List<UserDiagnosis> diagnoses) {
        List<DiagnosisJpaEntity> entities = diagnoses.stream()
                .map(DiagnosisJpaEntity::fromDomain)
                .toList();
        return jpaRepository.saveAll(entities).stream()
                .map(DiagnosisJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<UserDiagnosis> findByRoadmapId(Long roadmapId) {
        return jpaRepository.findByRoadmapId(roadmapId).stream()
                .map(DiagnosisJpaEntity::toDomain)
                .toList();
    }

    @Override
    public void deleteByRoadmapIds(List<Long> roadmapIds) {
        if (!roadmapIds.isEmpty()) {
            jpaRepository.deleteByRoadmapIds(roadmapIds);
        }
    }
}
