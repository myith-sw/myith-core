package com.myith.core.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserCompetencyJpaRepository
        extends JpaRepository<UserCompetencyJpaEntity, UserCompetencyJpaEntity.UserCompetencyId> {

    List<UserCompetencyJpaEntity> findByRoadmapId(Long roadmapId);
}
