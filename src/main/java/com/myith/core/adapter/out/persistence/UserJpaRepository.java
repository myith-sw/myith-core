package com.myith.core.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<UserJpaEntity, Long> {

    Optional<UserJpaEntity> findByGoogleIdAndDeletedAtIsNull(String googleId);

    Optional<UserJpaEntity> findByEmailAndDeletedAtIsNull(String email);

    Optional<UserJpaEntity> findByIdAndDeletedAtIsNull(Long id);

    @Query("""
        SELECT u FROM UserJpaEntity u
        WHERE u.deletedAt IS NULL
        AND u.lastActiveAt IS NOT NULL AND u.lastActiveAt < :cutoff
        AND (u.lastNudgeSentAt IS NULL OR u.lastNudgeSentAt < :nudgeCutoff)
        """)
    List<UserJpaEntity> findInactiveUsers(@Param("cutoff") Instant cutoff,
                                          @Param("nudgeCutoff") Instant nudgeCutoff);
}
