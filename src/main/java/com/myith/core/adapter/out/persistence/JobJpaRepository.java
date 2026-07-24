package com.myith.core.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobJpaRepository extends JpaRepository<JobJpaEntity, String> {

    List<JobJpaEntity> findAllByOrderByCategoryCodeAscJobNameAsc();
}
