package com.myith.core.adapter.out.persistence;

import com.myith.core.application.port.CharacterRepository;
import com.myith.core.domain.character.Character;
import org.springframework.stereotype.Repository;

import java.util.List;

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
}
