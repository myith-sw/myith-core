package com.myith.core.domain.roadmap;

import com.myith.core.domain.roadmap.MasteryMerger.CompetencyEntry;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * job_profile + 최종 M값으로 퀘스트 목록을 조립한다.
 *
 * 조립 순서 (D-6):
 *   1. 선후관계 위상정렬 (하드 제약)
 *   2. job_profile Lv 밴드에 퀘스트 배치
 *   3. 같은 레벨 내 Priority 내림차순 정렬
 *   4. M >= 0.66 -> ALREADY_KNOWN
 *   5. 선행 퀘스트 미완료 -> LOCKED, 그 외 OPEN
 */
public class RoadmapAssembler {

    public static List<Quest> assemble(Long roadmapId,
                                       ProfileData profile,
                                       Map<String, BigDecimal> selfAssessment,
                                       Map<String, CompetencyEntry> aiAssessment,
                                       BigDecimal alreadyKnownThreshold) {
        // 1. 위상정렬로 선후관계 검증 (순환 감지)
        List<String> topoOrder = topologicalSort(profile.skills(), profile.prerequisites());

        // 2. 레벨별 스킬 퀘스트 생성 + Priority 정렬
        List<Quest> quests = new ArrayList<>();
        for (LevelBand band : profile.levels()) {
            List<SkillWithPriority> skillsInLevel = new ArrayList<>();

            for (String skillCode : band.skills()) {
                SkillData skill = profile.skillMap().get(skillCode);
                if (skill == null) continue;

                BigDecimal m = MasteryMerger.merge(selfAssessment, aiAssessment, skillCode);
                double gap = 1.0 - m.doubleValue();
                double priority = gap * skill.prevalence();

                skillsInLevel.add(new SkillWithPriority(skill, m, priority));
            }

            // 3. Priority 내림차순 정렬 (레벨 내부에서만)
            skillsInLevel.sort(Comparator.comparingDouble(SkillWithPriority::priority).reversed());

            for (int i = 0; i < skillsInLevel.size(); i++) {
                SkillWithPriority sp = skillsInLevel.get(i);
                QuestTemplate template = profile.templateMap().get(sp.skill.skillCode());

                QuestStatus status = determineInitialStatus(sp.mastery, alreadyKnownThreshold);

                quests.add(Quest.createSkillQuest(
                        roadmapId,
                        sp.skill.skillCode(),
                        sp.skill.axisCode(),
                        band.level(),
                        i,
                        template != null ? template.title() : sp.skill.skillName(),
                        template != null ? template.completionCriteria() : null,
                        template != null ? template.ncsUnitCode() : null,
                        status
                ));
            }
        }

        // 활동형 퀘스트 추가
        if (profile.activityQuests() != null) {
            for (ActivityQuestData aq : profile.activityQuests()) {
                int orderInLevel = countQuestsInLevel(quests, aq.level());
                quests.add(Quest.createActivityQuest(
                        roadmapId,
                        aq.axisCode(),
                        aq.level(),
                        orderInLevel,
                        aq.title(),
                        aq.completionCriteria(),
                        QuestStatus.OPEN   // 활동형은 스킬이 없으므로 항상 OPEN
                ));
            }
        }

        // 5. 선행 퀘스트 미완료 OR 레벨 해금 미충족 -> LOCKED
        applyUnlockRules(quests, profile.prerequisites());

        return quests;
    }

    private static QuestStatus determineInitialStatus(BigDecimal mastery, BigDecimal threshold) {
        if (mastery.compareTo(threshold) >= 0) {
            return QuestStatus.ALREADY_KNOWN;
        }
        return QuestStatus.OPEN;
    }

    private static void applyUnlockRules(List<Quest> quests, List<Prerequisite> prerequisites) {
        // 최소 레벨 찾기
        int minLevel = quests.stream().mapToInt(Quest::getLevel).min().orElse(1);

        // 레벨별 전체 수 + 완료 수 집계
        Map<Integer, Long> totalPerLevel = quests.stream()
                .collect(Collectors.groupingBy(Quest::getLevel, Collectors.counting()));
        Map<Integer, Long> completedPerLevel = quests.stream()
                .collect(Collectors.groupingBy(Quest::getLevel,
                        Collectors.filtering(q -> q.getStatus().isCompleted(), Collectors.counting())));

        // skillCode -> 선행으로 필요한 스킬 목록
        Map<String, Set<String>> prereqMap = new HashMap<>();
        for (Prerequisite p : prerequisites) {
            prereqMap.computeIfAbsent(p.to(), k -> new HashSet<>()).add(p.from());
        }

        // 현재 완료된 스킬 목록
        Set<String> completedSkills = quests.stream()
                .filter(q -> q.getSkillCode() != null && q.getStatus().isCompleted())
                .map(Quest::getSkillCode)
                .collect(Collectors.toSet());

        for (int i = 0; i < quests.size(); i++) {
            Quest q = quests.get(i);
            // ALREADY_KNOWN은 건드리지 않는다
            if (q.getStatus() == QuestStatus.ALREADY_KNOWN) continue;

            // 레벨 해금: 이전 레벨 전부 완료해야 다음 레벨 해금
            boolean levelUnlocked = q.getLevel() <= minLevel
                    || completedPerLevel.getOrDefault(q.getLevel() - 1, 0L)
                       >= totalPerLevel.getOrDefault(q.getLevel() - 1, 0L);

            // 선행 충족 확인
            boolean prereqSatisfied = true;
            if (q.getSkillCode() != null) {
                Set<String> required = prereqMap.getOrDefault(q.getSkillCode(), Set.of());
                prereqSatisfied = completedSkills.containsAll(required);
            }

            if (!levelUnlocked || !prereqSatisfied) {
                Quest locked = Quest.createSkillQuest(
                        q.getRoadmapId(), q.getSkillCode(), q.getAxisCode(),
                        q.getLevel(), q.getOrderInLevel(), q.getTitle(),
                        q.getCompletionCriteria(), q.getNcsUnitCode(),
                        QuestStatus.LOCKED
                );
                quests.set(i, locked);
            }
        }
    }

    /**
     * 위상정렬: 선후관계에 순환이 없는지 검증하고 순서를 반환.
     * 순환이 있으면 IllegalStateException.
     */
    static List<String> topologicalSort(List<SkillData> skills, List<Prerequisite> prerequisites) {
        Map<String, List<String>> graph = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();

        for (SkillData s : skills) {
            graph.put(s.skillCode(), new ArrayList<>());
            inDegree.put(s.skillCode(), 0);
        }

        for (Prerequisite p : prerequisites) {
            if (graph.containsKey(p.from()) && graph.containsKey(p.to())) {
                graph.get(p.from()).add(p.to());
                inDegree.merge(p.to(), 1, Integer::sum);
            }
        }

        Queue<String> queue = new LinkedList<>();
        for (var entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) queue.add(entry.getKey());
        }

        List<String> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String node = queue.poll();
            sorted.add(node);
            for (String next : graph.getOrDefault(node, List.of())) {
                int newDegree = inDegree.merge(next, -1, Integer::sum);
                if (newDegree == 0) queue.add(next);
            }
        }

        if (sorted.size() != skills.size()) {
            throw new IllegalStateException("Circular dependency in skill prerequisites");
        }
        return sorted;
    }

    private static int countQuestsInLevel(List<Quest> quests, int level) {
        return (int) quests.stream().filter(q -> q.getLevel() == level).count();
    }

    // ===== 입력 데이터 레코드 =====

    public record ProfileData(
            List<SkillData> skills,
            List<LevelBand> levels,
            List<Prerequisite> prerequisites,
            List<ActivityQuestData> activityQuests,
            Map<String, SkillData> skillMap,
            Map<String, QuestTemplate> templateMap
    ) {}

    public record SkillData(String skillCode, String axisCode, String skillName,
                            double difficulty, double prevalence) {}

    public record LevelBand(int level, List<String> skills) {}

    public record Prerequisite(String from, String to) {}

    public record QuestTemplate(String skillCode, String title, String completionCriteria, String ncsUnitCode) {}

    public record ActivityQuestData(String axisCode, int level, String title, String completionCriteria) {}

    private record SkillWithPriority(SkillData skill, BigDecimal mastery, double priority) {}
}
