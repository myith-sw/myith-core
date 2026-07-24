package com.myith.core.adapter.out.persistence;

import com.myith.core.domain.character.Character;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "character")
public class CharacterJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "roadmap_id", nullable = false, unique = true)
    private Long roadmapId;

    @Column(name = "species", nullable = false)
    private String species;

    @Column(name = "nickname")
    private String nickname;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected CharacterJpaEntity() {}

    public static CharacterJpaEntity fromDomain(Character character) {
        CharacterJpaEntity entity = new CharacterJpaEntity();
        entity.id = character.getId();
        entity.userId = character.getUserId();
        entity.roadmapId = character.getRoadmapId();
        entity.species = character.getSpecies();
        entity.nickname = character.getNickname();
        entity.createdAt = character.getCreatedAt();
        entity.updatedAt = character.getUpdatedAt();
        return entity;
    }

    public Character toDomain() {
        return Character.restore(id, userId, roadmapId, species, nickname, createdAt, updatedAt);
    }
}
