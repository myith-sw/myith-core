package com.myith.core.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxJpaRepository extends JpaRepository<OutboxJpaEntity, Long> {
}
