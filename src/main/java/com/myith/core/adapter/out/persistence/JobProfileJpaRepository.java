package com.myith.core.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface JobProfileJpaRepository extends JpaRepository<JobProfileJpaEntity, JobProfileJpaEntity.JobProfileId> {

    @Query("SELECT jp FROM JobProfileJpaEntity jp WHERE jp.jobCode = :jobCode ORDER BY jp.version DESC LIMIT 1")
    Optional<JobProfileJpaEntity> findLatestByJobCode(String jobCode);
}
