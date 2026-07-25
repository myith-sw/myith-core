package com.myith.core.application.roadmap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myith.core.application.port.JobProfileReadRepository;
import com.myith.core.application.port.JobProfileReadRepository.JobProfileData;
import com.myith.core.application.port.JobReadRepository;
import com.myith.core.application.port.JobReadRepository.JobData;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class JobQueryService {

    private final JobReadRepository jobRepository;
    private final JobProfileReadRepository jobProfileRepository;
    private final ObjectMapper objectMapper;

    public JobQueryService(JobReadRepository jobRepository,
                           JobProfileReadRepository jobProfileRepository,
                           ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.jobProfileRepository = jobProfileRepository;
        this.objectMapper = objectMapper;
    }

    public List<CategoryDto> getJobList() {
        List<JobData> jobs = jobRepository.findAllOrderByCategoryAndName();

        Map<String, List<JobData>> grouped = jobs.stream()
                .collect(Collectors.groupingBy(JobData::categoryCode, LinkedHashMap::new, Collectors.toList()));

        List<CategoryDto> categories = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            List<JobData> categoryJobs = entry.getValue();
            String categoryName = categoryJobs.getFirst().categoryName();

            List<JobDto> jobDtos = categoryJobs.stream().map(job -> {
                Optional<JobProfileData> profile = jobProfileRepository.findLatestByJobCode(job.jobCode());
                List<String> keywords = profile.map(p -> extractKeywords(p.axes())).orElse(Collections.emptyList());
                boolean available = profile.isPresent();
                // TODO: profile 없을 때 JobProfileBuildRequested Outbox 발행
                return new JobDto(job.jobCode(), job.jobName(), job.tagline(), keywords, available);
            }).toList();

            categories.add(new CategoryDto(entry.getKey(), categoryName, jobDtos));
        }
        return categories;
    }

    public DiagnosisDto getDiagnosis(String jobCode) {
        JobProfileData profile = jobProfileRepository.findLatestByJobCode(jobCode)
                .orElseThrow(() -> new JobProfileNotFoundException(jobCode));

        List<QuestionDto> questions = parseQuestions(profile.questions());
        return new DiagnosisDto(profile.version(), questions);
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
