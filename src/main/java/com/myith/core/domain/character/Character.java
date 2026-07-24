package com.myith.core.domain.character;

import java.time.Instant;

public class Character {

    private Long id;
    private Long userId;
    private Long roadmapId;
    private String species;
    private String nickname;    // nullable
    private Instant createdAt;
    private Instant updatedAt;

    private Character() {}

    public static Character create(Long userId, Long roadmapId, String species, String nickname) {
        Character c = new Character();
        c.userId = userId;
        c.roadmapId = roadmapId;
        c.species = species;
        c.nickname = nickname;
        c.createdAt = Instant.now();
        c.updatedAt = c.createdAt;
        return c;
    }

    public static Character restore(Long id, Long userId, Long roadmapId, String species,
                                    String nickname, Instant createdAt, Instant updatedAt) {
        Character c = new Character();
        c.id = id;
        c.userId = userId;
        c.roadmapId = roadmapId;
        c.species = species;
        c.nickname = nickname;
        c.createdAt = createdAt;
        c.updatedAt = updatedAt;
        return c;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getRoadmapId() { return roadmapId; }
    public String getSpecies() { return species; }
    public String getNickname() { return nickname; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setId(Long id) { this.id = id; }
}
