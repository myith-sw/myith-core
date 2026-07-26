package com.myith.core.application.port;

import com.myith.core.domain.character.Character;

import java.util.List;
import java.util.Optional;

public interface CharacterRepository {
    Character save(Character character);
    List<String> findSpeciesByUserId(Long userId);
    List<Character> findByUserId(Long userId);
    Optional<Character> findByRoadmapId(Long roadmapId);
    void softDeleteByUserId(Long userId);
}
