package com.myith.core.adapter.out.persistence;

import com.myith.core.domain.user.User;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "users")
public class UserJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "google_id", unique = true)
    private String googleId;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(name = "profile_image_url", length = 512)
    private String profileImageUrl;

    @Column(name = "last_heartbeat_at")
    private Instant lastHeartbeatAt;

    @Column(name = "last_active_at")
    private Instant lastActiveAt;

    @Column(name = "last_nudge_sent_at")
    private Instant lastNudgeSentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected UserJpaEntity() {}

    public static UserJpaEntity fromDomain(User user) {
        UserJpaEntity entity = new UserJpaEntity();
        entity.id = user.getId();
        entity.email = user.getEmail();
        entity.googleId = user.getGoogleId();
        entity.nickname = user.getNickname();
        entity.profileImageUrl = user.getProfileImageUrl();
        entity.lastHeartbeatAt = user.getLastHeartbeatAt();
        entity.lastActiveAt = user.getLastActiveAt();
        entity.lastNudgeSentAt = user.getLastNudgeSentAt();
        entity.createdAt = user.getCreatedAt();
        entity.updatedAt = user.getUpdatedAt();
        entity.deletedAt = user.getDeletedAt();
        return entity;
    }

    public User toDomain() {
        User user = new User(id, email, googleId, nickname, profileImageUrl, lastActiveAt, createdAt, updatedAt);
        user.setId(id);
        return user;
    }

    public Long getId() { return id; }
    public void setLastNudgeSentAt(java.time.Instant lastNudgeSentAt) { this.lastNudgeSentAt = lastNudgeSentAt; }
}
