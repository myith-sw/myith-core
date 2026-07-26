package com.myith.core.adapter.in.web;

import com.myith.core.application.roadmap.JobQueryService;
import com.myith.core.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@Tag(name = "Catalog", description = "직무·자가진단 조회")
@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobQueryService jobQueryService;

    public JobController(JobQueryService jobQueryService) {
        this.jobQueryService = jobQueryService;
    }

    @Operation(
            summary = "직무 목록 조회",
            description = """
                    화면 2(캐릭터 생성) 진입 시 직무 카드 목록을 렌더링할 때 호출합니다.
                    available: false는 job_profile이 아직 준비되지 않은 직무입니다. '준비중' 잠긴 카드로 표시하고 선택을 막아야 합니다.
                    keywords는 job_profile 분야 축 상위 최대 5개입니다. 프로필이 없으면 빈 배열([])로 내려옵니다(null이 아닙니다).
                    sortOrder 기준으로 카테고리를 정렬하고, 카테고리 내 직무는 응답 순서를 그대로 유지하면 됩니다."""
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(mediaType = "application/json",
                    examples = @ExampleObject(value = """
                            {
                              "data": {
                                "categories": [
                                  {
                                    "categoryCode": "it",
                                    "categoryName": "IT·개발",
                                    "sortOrder": 1,
                                    "jobs": [
                                      {
                                        "jobCode": "server",
                                        "jobName": "백엔드 개발자",
                                        "tagline": "서버와 데이터베이스를 설계하고, 안정적으로 동작하는 API를 구현합니다.",
                                        "keywords": ["프로그래밍 기초","CS·자료구조","데이터입출력","서버·API","협업·형상관리"],
                                        "available": true
                                      },
                                      {
                                        "jobCode": "android",
                                        "jobName": "안드로이드 개발자",
                                        "tagline": "안드로이드 환경에서 동작하는 애플리케이션을 구현합니다.",
                                        "keywords": [],
                                        "available": false
                                      }
                                    ]
                                  }
                                ]
                              }
                            }""")))
    @GetMapping
    public ResponseEntity<ApiResponse<JobListResponse>> getJobs() {
        List<JobQueryService.CategoryDto> categories = jobQueryService.getJobList();
        return ResponseEntity.ok(ApiResponse.of(new JobListResponse(
                categories.stream().map(c -> new CategoryResponse(
                        c.categoryCode(), c.categoryName(), 1,
                        c.jobs().stream().map(j -> new JobResponse(
                                j.jobCode(), j.jobName(), j.tagline(), j.keywords(), j.available()
                        )).toList()
                )).toList()
        )));
    }

    @Operation(
            summary = "자가진단 문항 조회",
            description = """
                    화면 3(자가진단) 진입 시 문항 목록을 가져올 때 호출합니다.
                    문항 개수는 직무마다 다릅니다(6~10개). 배열 길이를 고정하지 마세요.
                    levels는 항상 4단계 고정입니다(unknown → heard → tried → independent 순서).
                    mastery는 표시 참고용이며, 서버에 응답을 보낼 때는 level의 id 값만 전송하면 됩니다.
                    profileVersion은 이 응답에서 받아 보관했다가 POST /api/roadmaps 요청 본문에 그대로 포함해야 합니다.
                    available: false 직무 코드로 요청하면 404가 반환됩니다."""
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "data": {
                                        "jobCode": "server",
                                        "profileVersion": 1,
                                        "questions": [
                                          {
                                            "skillCode": "git",
                                            "text": "버전관리로 협업한다",
                                            "axisCode": "collaboration",
                                            "axisName": "협업·형상관리",
                                            "sortOrder": 1
                                          },
                                          {
                                            "skillCode": "rest",
                                            "text": "REST API 서버를 구현한다",
                                            "axisCode": "server-api",
                                            "axisName": "서버·API",
                                            "sortOrder": 6
                                          }
                                        ],
                                        "levels": [
                                          { "id": "unknown", "label": "모름", "mastery": 0 },
                                          { "id": "heard", "label": "들어봄", "mastery": 0.33 },
                                          { "id": "tried", "label": "해봄", "mastery": 0.66 },
                                          { "id": "independent", "label": "혼자 가능", "mastery": 1.0 }
                                        ]
                                      }
                                    }"""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "JOB_PROFILE_NOT_READY — 해당 직무의 job_profile 미생성",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "error": {
                                        "code": "JOB_PROFILE_NOT_READY",
                                        "message": "해당 직무의 프로필이 아직 준비되지 않았습니다.",
                                        "requestId": "req_01J3ABC"
                                      }
                                    }""")))
    })
    @GetMapping("/{jobCode}/diagnosis")
    public ResponseEntity<ApiResponse<DiagnosisResponse>> getDiagnosis(
            @PathVariable String jobCode) {
        JobQueryService.DiagnosisDto dto = jobQueryService.getDiagnosis(jobCode);
        DiagnosisResponse response = new DiagnosisResponse(
                jobCode, dto.profileVersion(),
                dto.questions().stream().map(q -> new QuestionResponse(
                        q.skillCode(), q.text(), q.axisCode(), q.axisCode(), 0
                )).toList(),
                List.of(
                        new DiagnosisLevelResponse("unknown", "모름", BigDecimal.ZERO),
                        new DiagnosisLevelResponse("heard", "들어봄", new BigDecimal("0.33")),
                        new DiagnosisLevelResponse("tried", "해봄", new BigDecimal("0.66")),
                        new DiagnosisLevelResponse("independent", "혼자 가능", BigDecimal.ONE)
                )
        );
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    // ── Response DTOs ──

    @Schema(name = "JobListResponse")
    record JobListResponse(
            @Schema(description = "화면 2 직무 카테고리 목록. sortOrder 기준으로 정렬되어 있습니다.")
            List<CategoryResponse> categories
    ) {}

    @Schema(name = "CategoryResponse")
    record CategoryResponse(
            @Schema(description = "카테고리 코드", example = "it")
            String categoryCode,
            @Schema(description = "화면 2 카테고리 탭 이름", example = "IT·개발")
            String categoryName,
            @Schema(description = "정렬 순서", example = "1")
            int sortOrder,
            @Schema(description = "해당 카테고리의 직무 목록")
            List<JobResponse> jobs
    ) {}

    @Schema(name = "JobResponse")
    record JobResponse(
            @Schema(description = "직무 코드. enum이 아닌 자유 문자열입니다. 이후 자가진단 조회·로드맵 생성에 그대로 사용됩니다.", example = "server")
            String jobCode,
            @Schema(description = "화면 2 직무 카드 제목입니다.", example = "백엔드 개발자")
            String jobName,
            @Schema(description = "화면 2 직무 카드 설명문입니다.", example = "서버와 데이터베이스를 설계하고, 안정적으로 동작하는 API를 구현합니다.")
            String tagline,
            @Schema(description = "화면 2 직무 카드 하단 키워드 배열(최대 5개)입니다. 프로필이 없으면 빈 배열([])로 내려옵니다.",
                    example = "[\"프로그래밍 기초\",\"CS·자료구조\",\"데이터입출력\",\"서버·API\",\"협업·형상관리\"]")
            List<String> keywords,
            @Schema(description = "직무 선택 가능 여부입니다. false이면 '준비중' 잠긴 카드로 표시하고 선택을 비활성화해야 합니다.", example = "true")
            boolean available
    ) {}

    @Schema(name = "DiagnosisResponse")
    record DiagnosisResponse(
            @Schema(description = "직무 코드입니다.", example = "server")
            String jobCode,
            @Schema(description = "화면 3에서 받아 보관했다가 POST /api/roadmaps 요청 본문에 그대로 포함해야 하는 프로필 버전입니다.", example = "1")
            int profileVersion,
            @Schema(description = "화면 3 자가진단 문항 목록입니다. 직무마다 6~10개로 가변이므로 배열 길이를 고정하지 마세요.")
            List<QuestionResponse> questions,
            @Schema(description = "화면 3 4단계 선택지 버튼입니다. 항상 unknown → heard → tried → independent 순서로 내려옵니다.")
            List<DiagnosisLevelResponse> levels
    ) {}

    @Schema(name = "QuestionResponse")
    record QuestionResponse(
            @Schema(description = "스킬 코드입니다. POST /api/roadmaps의 answers[].skillCode에 그대로 사용합니다.", example = "git")
            String skillCode,
            @Schema(description = "화면 3에 표시할 문항 텍스트입니다.", example = "버전관리로 협업한다")
            String text,
            @Schema(description = "역량 축 코드입니다. enum이 아닌 자유 문자열로 직무마다 다릅니다.", example = "collaboration")
            String axisCode,
            @Schema(description = "역량 축 이름입니다. 문항 그룹 헤더나 레이더 차트 축 레이블로 사용합니다.", example = "협업·형상관리")
            String axisName,
            @Schema(description = "화면 3 문항 정렬 순서입니다.", example = "1")
            int sortOrder
    ) {}

    @Schema(name = "DiagnosisLevelResponse")
    record DiagnosisLevelResponse(
            @Schema(description = "진단 수준 ID입니다. POST /api/roadmaps의 answers[].level에 이 값을 그대로 전송합니다.", example = "tried",
                    allowableValues = {"unknown", "heard", "tried", "independent"})
            String id,
            @Schema(description = "화면 3 선택지 버튼 라벨입니다.", example = "해봄")
            String label,
            @Schema(description = "표시 참고용 내부 M값입니다. 서버로 전송하지 않아도 됩니다.", example = "0.66")
            BigDecimal mastery
    ) {}
}
