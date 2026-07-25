package com.myith.core.domain.user;

import java.time.Instant;

public class User {

    private Long id;
    private String email;
    private String googleId;
    private String nickname;
    private String profileImageUrl;
    private Instant lastHeartbeatAt;
    private Instant lastActiveAt;
    private Instant lastNudgeSentAt;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;

    public User(Long id, String email, String googleId, String nickname, String profileImageUrl,
                Instant lastActiveAt, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.email = email;
        this.googleId = googleId;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.lastActiveAt = lastActiveAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static User createFromGoogle(String email, String googleId, String nickname, String profileImageUrl) {
        Instant now = Instant.now();
        return new User(null, email, googleId, nickname, profileImageUrl, now, now, now);
    }

    public static User restore(Long id, String email, String googleId, String nickname,
                                String profileImageUrl, Instant lastHeartbeatAt, Instant lastActiveAt,
                                Instant lastNudgeSentAt, Instant createdAt, Instant updatedAt,
                                Instant deletedAt) {
        User user = new User(id, email, googleId, nickname, profileImageUrl, lastActiveAt, createdAt, updatedAt);
        user.id = id;
        user.lastHeartbeatAt = lastHeartbeatAt;
        user.lastNudgeSentAt = lastNudgeSentAt;
        user.deletedAt = deletedAt;
        return user;
    }

    public void updateProfile(String nickname, String profileImageUrl) {
        if (nickname != null) this.nickname = nickname;
        if (profileImageUrl != null) this.profileImageUrl = profileImageUrl;
        this.updatedAt = Instant.now();
    }

    public void anonymize() {
        this.email = "deleted_" + this.id + "@myith.local";
        this.googleId = null;
        this.nickname = "탈퇴한 사용자";
        this.profileImageUrl = null;
        this.deletedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void touchActivity(Instant now) {
        this.lastActiveAt = now;
        this.updatedAt = now;
    }

    public void touchHeartbeat(Instant now) {
        this.lastHeartbeatAt = now;
        this.updatedAt = now;
    }

    public void markNudgeSent(Instant now) {
        this.lastNudgeSentAt = now;
        this.updatedAt = now;
    }

    public boolean shouldThrottleActivity(Instant now, long throttleMinutes) {
        if (lastActiveAt == null) return false;
        return lastActiveAt.plusSeconds(throttleMinutes * 60).isAfter(now);
    }

    // Getters
    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getGoogleId() { return googleId; }
    public String getNickname() { return nickname; }
    public String getProfileImageUrl() { return profileImageUrl; }
    public Instant getLastHeartbeatAt() { return lastHeartbeatAt; }
    public Instant getLastActiveAt() { return lastActiveAt; }
    public Instant getLastNudgeSentAt() { return lastNudgeSentAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }

    public void setId(Long id) { this.id = id; }
}
