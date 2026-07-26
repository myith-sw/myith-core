package com.myith.core.application.roadmap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myith.core.application.port.JobProfileReadRepository;
import com.myith.core.application.port.JobProfileReadRepository.JobProfileData;
import com.myith.core.application.port.JobReadRepository;
import com.myith.core.application.port.JobReadRepository.JobData;
import com.myith.core.application.port.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class JobQueryService {

    private static final Logger log = LoggerFactory.getLogger(JobQueryService.class);

    private final JobReadRepository jobRepository;
    private final JobProfileReadRepository jobProfileRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public JobQueryService(JobReadRepository jobRepository,
                           JobProfileReadRepository jobProfileRepository,
                           OutboxRepository outboxRepository,
                           ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.jobProfileRepository = jobProfileRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
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
                if (!available) {
                    publishJobProfileBuildIfNeeded(job.jobCode());
                }
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

    private void publishJobProfileBuildIfNeeded(String jobCode) {
        Instant oneHourAgo = Instant.now().minusSeconds(3600);
        if (outboxRepository.existsRecentEvent(jobCode, "JobProfileBuildRequested", oneHourAgo)) {
            return;
        }

        UUID eventId = UUID.randomUUID();
        Map<String, Object> payload = Map.of("jobCode", jobCode);
        try {
            outboxRepository.save("Job", jobCode, eventId,
                    "JobProfileBuildRequested", objectMapper.writeValueAsString(payload));
            log.info("JobProfileBuildRequested published on-demand for jobCode={}", jobCode);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize JobProfileBuildRequested for {}", jobCode, e);
        }
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

    public List<AxisDto> getAxes(String jobCode) {
        JobProfileData profile = jobProfileRepository.findLatestByJobCode(jobCode)
                .orElseThrow(() -> new JobProfileNotFoundException(jobCode));
        try {
            List<Map<String, String>> axes = objectMapper.readValue(
                    profile.axes(), new TypeReference<>() {});
            return axes.stream()
                    .map(a -> new AxisDto(a.get("axisCode"), a.get("axisName")))
                    .toList();
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    public record AxisDto(String axisCode, String axisName) {}
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
