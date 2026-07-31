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

        // 3. 각 퀘스트의 목표 상태 계산 — 레벨 해금만으로 OPEN.
        // 선행관계는 레벨 배치에 이미 반영되어 있으므로 해금 판정에서 제외한다.
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

            QuestStatus target = levelUnlocked ? QuestStatus.OPEN : QuestStatus.LOCKED;

            // 이미 열린 퀘스트를 다시 잠그지 않는다 (단조성).
            // 퀘스트 추가·삭제·완료 취소로 레벨 total이 변해도
            // 이미 OPEN인 퀘스트가 LOCKED로 되돌아가면 안 된다.
            if (current == QuestStatus.OPEN && target == QuestStatus.LOCKED) continue;

            // 5. 현재 상태와 목표 상태가 다른 것만 반환
            if (current != target) {
                changes.add(new StatusChange(quest.getId(), target));
            }
        }

        return changes;
    }

    public record StatusChange(Long questId, QuestStatus newStatus) {}
}
