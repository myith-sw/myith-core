package com.myith.core.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxJpaRepository extends JpaRepository<OutboxJpaEntity, Long> {
    List<OutboxJpaEntity> findByStatusOrderByCreatedAtAsc(String status);
}
