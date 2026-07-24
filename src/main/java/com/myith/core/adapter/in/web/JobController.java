package com.myith.core.adapter.in.web;

import com.myith.core.application.roadmap.JobQueryService;
import com.myith.core.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobQueryService jobQueryService;

    public JobController(JobQueryService jobQueryService) {
        this.jobQueryService = jobQueryService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<JobListResponse>> getJobs() {
        List<JobQueryService.CategoryDto> categories = jobQueryService.getJobList();
        return ResponseEntity.ok(ApiResponse.success(new JobListResponse(categories)));
    }

    @GetMapping("/{jobCode}/diagnosis")
    public ResponseEntity<ApiResponse<JobQueryService.DiagnosisDto>> getDiagnosis(
            @PathVariable String jobCode) {
        JobQueryService.DiagnosisDto diagnosis = jobQueryService.getDiagnosis(jobCode);
        return ResponseEntity.ok(ApiResponse.success(diagnosis));
    }

    record JobListResponse(List<JobQueryService.CategoryDto> categories) {}
}
