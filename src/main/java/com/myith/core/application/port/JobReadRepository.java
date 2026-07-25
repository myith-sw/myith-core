package com.myith.core.application.port;

import java.util.List;
import java.util.Optional;

public interface JobReadRepository {

    List<JobData> findAllOrderByCategoryAndName();

    Optional<JobData> findByJobCode(String jobCode);

    record JobData(String jobCode, String jobName, String categoryCode,
                   String categoryName, String tagline) {}
}
