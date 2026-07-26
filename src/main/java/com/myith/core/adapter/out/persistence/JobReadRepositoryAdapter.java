package com.myith.core.adapter.out.persistence;

import com.myith.core.application.port.JobReadRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JobReadRepositoryAdapter implements JobReadRepository {

    private final JobJpaRepository jpaRepository;

    public JobReadRepositoryAdapter(JobJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<JobData> findAllOrderByCategoryAndName() {
        return jpaRepository.findAllByOrderByCategoryCodeAscJobNameAsc().stream()
                .map(e -> new JobData(e.getJobCode(), e.getJobName(),
                        e.getCategoryCode(), e.getCategoryName(), e.getTagline()))
                .toList();
    }

    @Override
    public Optional<JobData> findByJobCode(String jobCode) {
        return jpaRepository.findById(jobCode)
                .map(e -> new JobData(e.getJobCode(), e.getJobName(),
                        e.getCategoryCode(), e.getCategoryName(), e.getTagline()));
    }

    @Override
    public List<String> findAllJobCodes() {
        return jpaRepository.findAll().stream()
                .map(JobJpaEntity::getJobCode)
                .toList();
    }
}
