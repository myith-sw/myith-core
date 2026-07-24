package com.myith.core.application.roadmap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myith.core.adapter.out.persistence.JobJpaEntity;
import com.myith.core.adapter.out.persistence.JobJpaRepository;
import com.myith.core.adapter.out.persistence.JobProfileJpaEntity;
import com.myith.core.adapter.out.persistence.JobProfileJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class JobQueryService {

    private final JobJpaRepository jobRepository;
    private final JobProfileJpaRepository jobProfileRepository;
    private final ObjectMapper objectMapper;

    public JobQueryService(JobJpaRepository jobRepository,
                           JobProfileJpaRepository jobProfileRepository,
                           ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.jobProfileRepository = jobProfileRepository;
        this.objectMapper = objectMapper;
    }

    public List<CategoryDto> getJobList() {
        List<JobJpaEntity> jobs = jobRepository.findAllByOrderByCategoryCodeAscJobNameAsc();

        Map<String, List<JobJpaEntity>> grouped = jobs.stream()
                .collect(Collectors.groupingBy(JobJpaEntity::getCategoryCode, LinkedHashMap::new, Collectors.toList()));

        List<CategoryDto> categories = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            List<JobJpaEntity> categoryJobs = entry.getValue();
            String categoryName = categoryJobs.getFirst().getCategoryName();

            List<JobDto> jobDtos = categoryJobs.stream().map(job -> {
                Optional<JobProfileJpaEntity> profile = jobProfileRepository.findLatestByJobCode(job.getJobCode());
                List<String> keywords = profile.map(p -> extractKeywords(p.getAxes())).orElse(Collections.emptyList());
                boolean available = profile.isPresent();
                // TODO: profile 없을 때 JobProfileBuildRequested Outbox 발행
                return new JobDto(job.getJobCode(), job.getJobName(), job.getTagline(), keywords, available);
            }).toList();

            categories.add(new CategoryDto(entry.getKey(), categoryName, jobDtos));
        }
        return categories;
    }

    public DiagnosisDto getDiagnosis(String jobCode) {
        JobProfileJpaEntity profile = jobProfileRepository.findLatestByJobCode(jobCode)
                .orElseThrow(() -> new JobProfileNotFoundException(jobCode));

        List<QuestionDto> questions = parseQuestions(profile.getQuestions());
        return new DiagnosisDto(profile.getVersion(), questions);
    }

    private List<String> extractKeywords(String axesJson) {
        try {
            List<Map<String, String>> axes = objectMapper.readValue(axesJson, new TypeReference<>() {});
            return axes.stream()
                    .map(a -> a.get("axisName"))
                    .limit(5)
                    .toList();
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    private List<QuestionDto> parseQuestions(String questionsJson) {
        try {
            return objectMapper.readValue(questionsJson, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    public record CategoryDto(String categoryCode, String categoryName, List<JobDto> jobs) {}
    public record JobDto(String jobCode, String jobName, String tagline, List<String> keywords, boolean available) {}
    public record DiagnosisDto(int profileVersion, List<QuestionDto> questions) {}
    public record QuestionDto(String skillCode, String text, String axisCode) {}

    public static class JobProfileNotFoundException extends RuntimeException {
        public JobProfileNotFoundException(String jobCode) {
            super("Job profile not found: " + jobCode);
        }
    }
}
