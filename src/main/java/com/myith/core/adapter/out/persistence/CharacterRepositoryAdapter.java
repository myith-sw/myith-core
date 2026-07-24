package com.myith.core.adapter.out.persistence;

import com.myith.core.application.port.CharacterRepository;
import com.myith.core.domain.character.Character;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class CharacterRepositoryAdapter implements CharacterRepository {

    private final CharacterJpaRepository jpaRepository;

    public CharacterRepositoryAdapter(CharacterJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Character save(Character character) {
        CharacterJpaEntity entity = CharacterJpaEntity.fromDomain(character);
        CharacterJpaEntity saved = jpaRepository.save(entity);
        return saved.toDomain();
    }

    @Override
    public List<String> findSpeciesByUserId(Long userId) {
        return jpaRepository.findDistinctSpeciesByUserId(userId);
    }

    @Override
    public List<Character> findByUserId(Long userId) {
        return jpaRepository.findByUserIdAndDeletedAtIsNull(userId).stream()
                .map(CharacterJpaEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<Character> findByRoadmapId(Long roadmapId) {
        return jpaRepository.findByRoadmapIdAndDeletedAtIsNull(roadmapId)
                .map(CharacterJpaEntity::toDomain);
    }
}
