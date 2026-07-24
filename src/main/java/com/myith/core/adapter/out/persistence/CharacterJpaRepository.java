package com.myith.core.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CharacterJpaRepository extends JpaRepository<CharacterJpaEntity, Long> {

    List<CharacterJpaEntity> findByUserIdAndDeletedAtIsNull(Long userId);

    @Query("SELECT DISTINCT c.species FROM CharacterJpaEntity c WHERE c.userId = :userId AND c.deletedAt IS NULL")
    List<String> findDistinctSpeciesByUserId(@Param("userId") Long userId);
}
