package com.myith.core.application.port;

import com.myith.core.domain.character.Character;

import java.util.List;

public interface CharacterRepository {
    Character save(Character character);
    List<String> findSpeciesByUserId(Long userId);
}
