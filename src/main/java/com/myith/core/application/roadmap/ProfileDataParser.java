package com.myith.core.application.roadmap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myith.core.domain.roadmap.RoadmapAssembler.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * job_profile의 JSONB 컬럼들을 도메인 레코드로 변환한다.
 */
public class ProfileDataParser {

    private final ObjectMapper objectMapper;

    public ProfileDataParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ProfileData parse(String skillsJson, String levelsJson, String prerequisitesJson,
                             String questTemplatesJson, String activityQuestsJson) {
        List<SkillData> skills = parseSkills(skillsJson);
        List<LevelBand> levels = parseLevels(levelsJson);
        List<Prerequisite> prerequisites = parseJson(prerequisitesJson, new TypeReference<>() {});
        List<QuestTemplate> templates = parseJson(questTemplatesJson, new TypeReference<>() {});
        List<ActivityQuestData> activityQuests = parseJson(activityQuestsJson, new TypeReference<>() {});

        Map<String, SkillData> skillMap = skills.stream()
                .collect(Collectors.toMap(SkillData::skillCode, Function.identity()));
        Map<String, QuestTemplate> templateMap = templates.stream()
                .collect(Collectors.toMap(QuestTemplate::skillCode, Function.identity()));

        return new ProfileData(skills, levels, prerequisites, activityQuests, skillMap, templateMap);
    }

    /**
     * skills JSONB는 Worker가 d/p 축약어를 쓰고 Core는 difficulty/prevalence를 기대한다.
     * 둘 다 호환되도록 매핑한다.
     */
    private List<SkillData> parseSkills(String skillsJson) {
        List<Map<String, Object>> raw = parseJson(skillsJson, new TypeReference<>() {});
        return raw.stream().map(m -> {
            String skillCode = (String) m.get("skillCode");
            String axisCode = (String) m.get("axisCode");
            String skillName = (String) m.get("skillName");
            // Worker는 "d"/"p", 기존 시드는 "difficulty"/"prevalence"
            double difficulty = toDouble(m.getOrDefault("d", m.getOrDefault("difficulty", 0.5)));
            double prevalence = toDouble(m.getOrDefault("p", m.getOrDefault("prevalence", 0.5)));
            return new SkillData(skillCode, axisCode, skillName, difficulty, prevalence);
        }).toList();
    }

    private static double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        return 0.5;
    }

    /**
     * levels JSONB는 Worker가 "skillCodes"로 보내고 Core는 "skills"로 읽는다.
     * domain record(LevelBand)에 Jackson 애노테이션을 넣지 않기 위해 여기서 수동 매핑한다.
     */
    @SuppressWarnings("unchecked")
    private List<LevelBand> parseLevels(String levelsJson) {
        List<Map<String, Object>> raw = parseJson(levelsJson, new TypeReference<>() {});
        return raw.stream().map(m -> {
            int level = ((Number) m.get("level")).intValue();
            // Worker는 "skillCodes", 기존 시드는 "skills" — 둘 다 호환
            List<String> skillCodes = (List<String>) m.getOrDefault("skillCodes", m.get("skills"));
            return new LevelBand(level, skillCodes != null ? skillCodes : List.of());
        }).toList();
    }

    private <T> T parseJson(String json, TypeReference<T> typeRef) {
        if (json == null || json.isBlank()) {
            try {
                return objectMapper.readValue("[]", typeRef);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse profile JSON", e);
        }
    }
}
