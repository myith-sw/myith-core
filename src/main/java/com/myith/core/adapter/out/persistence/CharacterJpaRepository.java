package com.myith.core.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CharacterJpaRepository extends JpaRepository<CharacterJpaEntity, Long> {

    List<CharacterJpaEntity> findByUserIdAndDeletedAtIsNull(Long userId);

    Optional<CharacterJpaEntity> findByRoadmapIdAndDeletedAtIsNull(Long roadmapId);

    @Query("SELECT DISTINCT c.species FROM CharacterJpaEntity c WHERE c.userId = :userId AND c.deletedAt IS NULL")
    List<String> findDistinctSpeciesByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE CharacterJpaEntity c SET c.deletedAt = CURRENT_TIMESTAMP WHERE c.userId = :userId AND c.deletedAt IS NULL")
    void softDeleteByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE CharacterJpaEntity c SET c.deletedAt = CURRENT_TIMESTAMP WHERE c.id = :id AND c.deletedAt IS NULL")
    void softDeleteById(@Param("id") Long id);
}
