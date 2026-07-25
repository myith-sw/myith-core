package com.myith.core.application.port;

import java.util.Optional;

public interface JobProfileReadRepository {

    Optional<JobProfileData> findLatestByJobCode(String jobCode);

    Optional<JobProfileData> findByJobCodeAndVersion(String jobCode, int version);

    record JobProfileData(String jobCode, int version, String axes, String skills,
                          String levels, String prerequisites, String questions,
                          String questTemplates, String activityQuests) {}
}
