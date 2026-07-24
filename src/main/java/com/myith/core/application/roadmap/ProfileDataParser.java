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
        List<SkillData> skills = parseJson(skillsJson, new TypeReference<>() {});
        List<LevelBand> levels = parseJson(levelsJson, new TypeReference<>() {});
        List<Prerequisite> prerequisites = parseJson(prerequisitesJson, new TypeReference<>() {});
        List<QuestTemplate> templates = parseJson(questTemplatesJson, new TypeReference<>() {});
        List<ActivityQuestData> activityQuests = parseJson(activityQuestsJson, new TypeReference<>() {});

        Map<String, SkillData> skillMap = skills.stream()
                .collect(Collectors.toMap(SkillData::skillCode, Function.identity()));
        Map<String, QuestTemplate> templateMap = templates.stream()
                .collect(Collectors.toMap(QuestTemplate::skillCode, Function.identity()));

        return new ProfileData(skills, levels, prerequisites, activityQuests, skillMap, templateMap);
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
