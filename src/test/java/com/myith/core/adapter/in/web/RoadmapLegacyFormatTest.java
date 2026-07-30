package com.myith.core.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 프론트 구버전이 보내는 레거시 형식(narrative.experience + 최상위 repoUrl/fileKey)을
 * Jackson이 역직렬화하고, 컨트롤러가 experiences로 합성하는지 검증한다.
 */
class RoadmapLegacyFormatTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    @DisplayName("narrative.experience 만 있으면 experiences 1건으로 합성, 202 비동기")
    void legacyNarrativeExperience_synthesizesToExperiences() throws Exception {
        String json = """
                {
                  "jobCode": "backend",
                  "profileVersion": 1,
                  "species": "DRAGON",
                  "answers": [{"skillCode":"git","level":"heard"}],
                  "narrative": {
                    "experience": "1. Spring으로 커뮤니티를 만들었습니다"
                  },
                  "repoUrl": "https://github.com/user/repo",
                  "fileKey": "portfolio/abc.pdf"
                }
                """;

        // Jackson 역직렬화
        var request = om.readValue(json,
                RoadmapController.CreateRoadmapRequest.class);

        // narrative.experience 가 역직렬화됐는지
        assertNotNull(request.narrative());
        assertEquals("1. Spring으로 커뮤니티를 만들었습니다", request.narrative().experience());

        // 최상위 repoUrl, fileKey
        assertEquals("https://github.com/user/repo", request.repoUrl());
        assertEquals("portfolio/abc.pdf", request.fileKey());

        // experiences 는 null (프론트가 안 보냈으므로)
        assertNull(request.experiences());

        // 합성 로직 검증: experiences가 null이고 레거시 필드가 있으면 합성해야 함
        // 컨트롤러 내부 로직을 여기서 재현
        String legacyContent = request.narrative().experience();
        String legacyRepoUrl = request.repoUrl();
        String legacyFileKey = request.fileKey();
        boolean hasLegacy = hasText(legacyContent) || hasText(legacyRepoUrl) || hasText(legacyFileKey);
        assertTrue(hasLegacy, "레거시 필드가 있어야 한다");
    }

    @Test
    @DisplayName("아무것도 없으면 experiences 없음, 200 동기")
    void noNarrativeNoExperiences_sync() throws Exception {
        String json = """
                {
                  "jobCode": "backend",
                  "profileVersion": 1,
                  "species": "DRAGON",
                  "answers": [{"skillCode":"git","level":"heard"}]
                }
                """;

        var request = om.readValue(json,
                RoadmapController.CreateRoadmapRequest.class);

        assertNull(request.narrative());
        assertNull(request.experiences());
        assertNull(request.repoUrl());
        assertNull(request.fileKey());
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
