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
                    화면 2(캐릭터 생성) 직무 카드 목록에 사용한다.
                    available: false는 job_profile이 아직 없는 직무. 프론트가 '준비중' 잠긴 카드로 표시한다.
                    keywords는 job_profile 분야 축 상위 5개. 프로필이 없으면 빈 배열이다(null 아님)."""
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
                                        "keywords": ["프로그래밍 기초","CS·자료구조","데이터입출력","서버·API","협업·형상관리","배포·운영"],
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
                    화면 3(자가진단) 문항 목록을 반환한다.
                    문항 개수는 직무마다 다르다(6~10개). 배열 길이를 고정하지 않는다.
                    levels는 4단계 고정이다. mastery는 참고용이며 프론트는 id만 서버로 보낸다.
                    profileVersion은 프론트가 보관했다가 POST /api/roadmaps 요청에 그대로 실어 보낸다."""
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
            @Schema(description = "화면 2 직무 카테고리 목록")
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
            @Schema(description = "직무 코드. enum이 아닌 자유 문자열", example = "server")
            String jobCode,
            @Schema(description = "화면 2 직무 카드 제목", example = "백엔드 개발자")
            String jobName,
            @Schema(description = "화면 2 직무 카드 설명문", example = "서버와 데이터베이스를 설계하고, 안정적으로 동작하는 API를 구현합니다.")
            String tagline,
            @Schema(description = "화면 2 직무 카드 하단 키워드 최대 5개. 프로필 없으면 빈 배열",
                    example = "[\"프로그래밍 기초\",\"CS·자료구조\",\"데이터입출력\",\"서버·API\",\"협업·형상관리\"]")
            List<String> keywords,
            @Schema(description = "화면 2 false면 '준비중' 잠긴 카드", example = "true")
            boolean available
    ) {}

    @Schema(name = "DiagnosisResponse")
    record DiagnosisResponse(
            @Schema(description = "직무 코드", example = "server")
            String jobCode,
            @Schema(description = "화면 3에서 받아 로드맵 생성 요청에 재전송하는 프로필 버전", example = "1")
            int profileVersion,
            @Schema(description = "화면 3 자가진단 문항 목록. 직무마다 6~10개 가변")
            List<QuestionResponse> questions,
            @Schema(description = "화면 3 4단계 선택지 버튼")
            List<DiagnosisLevelResponse> levels
    ) {}

    @Schema(name = "QuestionResponse")
    record QuestionResponse(
            @Schema(description = "스킬 코드", example = "git")
            String skillCode,
            @Schema(description = "화면 3 문항 텍스트", example = "버전관리로 협업한다")
            String text,
            @Schema(description = "역량 축 코드. enum이 아닌 자유 문자열", example = "collaboration")
            String axisCode,
            @Schema(description = "역량 축 이름", example = "협업·형상관리")
            String axisName,
            @Schema(description = "정렬 순서", example = "1")
            int sortOrder
    ) {}

    @Schema(name = "DiagnosisLevelResponse")
    record DiagnosisLevelResponse(
            @Schema(description = "진단 수준 ID. unknown|heard|tried|independent", example = "tried")
            String id,
            @Schema(description = "화면 3 버튼 라벨", example = "해봄")
            String label,
            @Schema(description = "참고용 M값. 프론트는 id만 서버로 보낸다", example = "0.66")
            BigDecimal mastery
    ) {}
}
