package com.myith.core.adapter.out.persistence;

import com.myith.core.application.port.JobProfileReadRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JobProfileReadRepositoryAdapter implements JobProfileReadRepository {

    private final JobProfileJpaRepository jpaRepository;

    public JobProfileReadRepositoryAdapter(JobProfileJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<JobProfileData> findLatestByJobCode(String jobCode) {
        return jpaRepository.findLatestByJobCode(jobCode).map(this::toData);
    }

    @Override
    public Optional<JobProfileData> findByJobCodeAndVersion(String jobCode, int version) {
        return jpaRepository.findById(new JobProfileJpaEntity.JobProfileId(jobCode, version))
                .map(this::toData);
    }

    private JobProfileData toData(JobProfileJpaEntity e) {
        return new JobProfileData(e.getJobCode(), e.getVersion(), e.getAxes(), e.getSkills(),
                e.getLevels(), e.getPrerequisites(), e.getQuestions(),
                e.getQuestTemplates(), e.getActivityQuests());
    }
}
