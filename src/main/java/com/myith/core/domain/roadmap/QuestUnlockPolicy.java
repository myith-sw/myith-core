package com.myith.core.domain.roadmap;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 퀘스트 LOCKED/OPEN 상태를 레벨 해금 + 선행관계로 재계산한다.
 * 순수 함수. 프레임워크 의존 없음.
 */
public class QuestUnlockPolicy {

    /**
     * 퀘스트 목록 전체의 LOCKED/OPEN 상태를 재계산해 변경이 필요한 것만 반환한다.
     * 레벨 해금 규칙: 이전 레벨의 퀘스트를 전부 완료(DONE + ALREADY_KNOWN)해야 다음 레벨이 열린다.
     *
     * @param quests        전체 퀘스트 목록
     * @param prerequisites 선행관계 (from → to: from이 완료되어야 to가 열림)
     * @return 상태 변경이 필요한 퀘스트 목록
     */
    public static List<StatusChange> recompute(
            List<Quest> quests,
            List<RoadmapAssembler.Prerequisite> prerequisites) {

        // 1. 레벨별 전체 개수 + 완료 개수 집계
        Map<Integer, Long> totalCountByLevel = quests.stream()
                .collect(Collectors.groupingBy(Quest::getLevel, Collectors.counting()));
        Map<Integer, Long> completedCountByLevel = quests.stream()
                .filter(q -> q.getStatus().isCompleted())
                .collect(Collectors.groupingBy(Quest::getLevel, Collectors.counting()));

        // 2. 레벨별 해금 여부: 이전 레벨 전부 완료해야 다음 레벨 해금
        List<Integer> sortedLevels = quests.stream()
                .map(Quest::getLevel)
                .distinct()
                .sorted()
                .toList();

        Set<Integer> unlockedLevels = new HashSet<>();
        for (int i = 0; i < sortedLevels.size(); i++) {
            int level = sortedLevels.get(i);
            if (i == 0) {
                // 최소 레벨은 항상 해금
                unlockedLevels.add(level);
            } else {
                int prevLevel = sortedLevels.get(i - 1);
                long prevTotal = totalCountByLevel.getOrDefault(prevLevel, 0L);
                long prevCompleted = completedCountByLevel.getOrDefault(prevLevel, 0L);
                if (unlockedLevels.contains(prevLevel) && prevCompleted >= prevTotal) {
                    unlockedLevels.add(level);
                }
            }
        }

        // 3. 선행관계 맵 구성: skillCode → 선행 스킬 목록
        Map<String, Set<String>> prereqMap = new HashMap<>();
        for (RoadmapAssembler.Prerequisite p : prerequisites) {
            prereqMap.computeIfAbsent(p.to(), k -> new HashSet<>()).add(p.from());
        }

        // 완료된 스킬 집합
        Set<String> completedSkills = quests.stream()
                .filter(q -> q.getSkillCode() != null && q.getStatus().isCompleted())
                .map(Quest::getSkillCode)
                .collect(Collectors.toSet());

        // 4. 각 퀘스트의 목표 상태 계산
        List<StatusChange> changes = new ArrayList<>();
        for (Quest quest : quests) {
            QuestStatus current = quest.getStatus();

            // DONE / ALREADY_KNOWN / PENDING → 절대 변경하지 않음
            if (current == QuestStatus.DONE
                    || current == QuestStatus.ALREADY_KNOWN
                    || current == QuestStatus.PENDING) {
                continue;
            }

            // 레벨 해금 여부
            boolean levelUnlocked = unlockedLevels.contains(quest.getLevel());

            // 선행관계 충족 여부
            boolean prereqsMet;
            if (quest.getSkillCode() == null) {
                // 활동형/사용자정의는 선행관계 제약을 받지 않음
                prereqsMet = true;
            } else {
                Set<String> required = prereqMap.get(quest.getSkillCode());
                prereqsMet = (required == null || completedSkills.containsAll(required));
            }

            QuestStatus target = (levelUnlocked && prereqsMet) ? QuestStatus.OPEN : QuestStatus.LOCKED;

            // 5. 현재 상태와 목표 상태가 다른 것만 반환
            if (current != target) {
                changes.add(new StatusChange(quest.getId(), target));
            }
        }

        return changes;
    }

    public record StatusChange(Long questId, QuestStatus newStatus) {}
}
