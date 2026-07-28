package com.myith.core.domain.roadmap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class QuestUnlockPolicyTest {

    // ===== 헬퍼 메서드 =====

    private static long idSeq = 1;

    private static Quest skillQuest(int level, String skillCode, QuestStatus status) {
        long id = idSeq++;
        return Quest.restore(
                id, 1L, skillCode, "axis1",
                level, 0, "Quest " + skillCode,
                null, null, null,
                QuestSource.SKILL, status, null,
                0, Instant.now(), Instant.now()
        );
    }

    private static Quest activityQuest(int level, QuestStatus status) {
        long id = idSeq++;
        return Quest.restore(
                id, 1L, null, "axis1",
                level, 0, "Activity Quest " + id,
                null, null, null,
                QuestSource.ACTIVITY, status, null,
                0, Instant.now(), Instant.now()
        );
    }

    private static Map<Long, QuestStatus> toMap(List<QuestUnlockPolicy.StatusChange> changes) {
        return changes.stream()
                .collect(Collectors.toMap(
                        QuestUnlockPolicy.StatusChange::questId,
                        QuestUnlockPolicy.StatusChange::newStatus
                ));
    }

    // ===== 테스트 케이스 =====

    @Test
    @DisplayName("Lv1은 완료가 0개여도 OPEN (최소 레벨은 항상 열림)")
    void minLevelAlwaysOpen() {
        idSeq = 1;
        Quest q1 = skillQuest(1, "java", QuestStatus.LOCKED);
        Quest q2 = skillQuest(1, "spring", QuestStatus.LOCKED);
        Quest q3 = skillQuest(2, "jpa", QuestStatus.LOCKED);

        var changes = QuestUnlockPolicy.recompute(List.of(q1, q2, q3), List.of());

        Map<Long, QuestStatus> map = toMap(changes);
        assertEquals(QuestStatus.OPEN, map.get(q1.getId()));
        assertEquals(QuestStatus.OPEN, map.get(q2.getId()));
        assertFalse(map.containsKey(q3.getId()));
    }

    @Test
    @DisplayName("Lv1 전부 완료하면 Lv2 해금")
    void lv2UnlocksAfterAllLv1Completed() {
        idSeq = 1;
        Quest q1 = skillQuest(1, "java", QuestStatus.DONE);
        Quest q2 = skillQuest(1, "spring", QuestStatus.DONE);
        Quest q3 = skillQuest(2, "jpa", QuestStatus.LOCKED);

        var changes = QuestUnlockPolicy.recompute(List.of(q1, q2, q3), List.of());

        Map<Long, QuestStatus> map = toMap(changes);
        assertEquals(QuestStatus.OPEN, map.get(q3.getId()));
    }

    @Test
    @DisplayName("Lv1 중 하나라도 미완료면 Lv2 잠김 유지")
    void lv2StaysLockedIfAnyLv1Incomplete() {
        idSeq = 1;
        Quest q1 = skillQuest(1, "java", QuestStatus.DONE);
        Quest q2 = skillQuest(1, "spring", QuestStatus.OPEN);   // 미완료
        Quest q3 = skillQuest(2, "jpa", QuestStatus.LOCKED);

        var changes = QuestUnlockPolicy.recompute(List.of(q1, q2, q3), List.of());

        Map<Long, QuestStatus> map = toMap(changes);
        assertFalse(map.containsKey(q3.getId()));  // LOCKED 유지
    }

    @Test
    @DisplayName("ALREADY_KNOWN도 완료로 집계되어 다음 레벨을 연다")
    void alreadyKnownCountsAsCompleted() {
        idSeq = 1;
        Quest q1 = skillQuest(1, "java", QuestStatus.DONE);
        Quest q2 = skillQuest(1, "spring", QuestStatus.ALREADY_KNOWN);
        Quest q3 = skillQuest(2, "jpa", QuestStatus.LOCKED);

        var changes = QuestUnlockPolicy.recompute(List.of(q1, q2, q3), List.of());

        Map<Long, QuestStatus> map = toMap(changes);
        assertEquals(QuestStatus.OPEN, map.get(q3.getId()));
    }

    @Test
    @DisplayName("선행관계 미충족이면 레벨이 열려도 LOCKED")
    void prerequisiteNotMetKeepsLocked() {
        idSeq = 1;
        Quest q1 = skillQuest(1, "java", QuestStatus.DONE);
        Quest q2 = skillQuest(1, "spring", QuestStatus.DONE);
        Quest q3 = skillQuest(2, "jpa", QuestStatus.LOCKED);
        Quest q4 = skillQuest(2, "security", QuestStatus.LOCKED);

        var prereqs = List.of(
                new RoadmapAssembler.Prerequisite("spring", "jpa"),
                new RoadmapAssembler.Prerequisite("jpa", "security")
        );

        var changes = QuestUnlockPolicy.recompute(List.of(q1, q2, q3, q4), prereqs);

        Map<Long, QuestStatus> map = toMap(changes);
        assertEquals(QuestStatus.OPEN, map.get(q3.getId()));
        assertFalse(map.containsKey(q4.getId()));  // jpa 미완료 → security LOCKED
    }

    @Test
    @DisplayName("완료를 해제해도 이미 OPEN인 퀘스트는 LOCKED로 되돌아가지 않는다 (단조성)")
    void undoCompletionDoesNotRelockOpenQuest() {
        idSeq = 1;
        Quest q1 = skillQuest(1, "java", QuestStatus.DONE);
        Quest q2 = skillQuest(1, "spring", QuestStatus.OPEN);   // 완료 해제됨
        Quest q3 = skillQuest(2, "jpa", QuestStatus.OPEN);      // 이전에 열렸었음

        var changes = QuestUnlockPolicy.recompute(List.of(q1, q2, q3), List.of());

        Map<Long, QuestStatus> map = toMap(changes);
        assertFalse(map.containsKey(q3.getId()), "OPEN 퀘스트는 LOCKED로 되돌아가지 않아야 한다");
    }

    @Test
    @DisplayName("이미 DONE인 퀘스트는 재잠금되지 않는다")
    void doneQuestNeverRelocked() {
        idSeq = 1;
        Quest q1 = skillQuest(1, "java", QuestStatus.OPEN);
        Quest q2 = skillQuest(2, "jpa", QuestStatus.DONE);

        var changes = QuestUnlockPolicy.recompute(List.of(q1, q2), List.of());

        Map<Long, QuestStatus> map = toMap(changes);
        assertFalse(map.containsKey(q2.getId()));
    }

    @Test
    @DisplayName("활동형(skillCode=null)도 레벨 규칙을 받는다 - 해금")
    void activityQuestFollowsLevelRules() {
        idSeq = 1;
        Quest q1 = skillQuest(1, "java", QuestStatus.DONE);
        Quest q2 = skillQuest(1, "spring", QuestStatus.DONE);
        Quest activity = activityQuest(2, QuestStatus.LOCKED);

        var changes = QuestUnlockPolicy.recompute(List.of(q1, q2, activity), List.of());

        Map<Long, QuestStatus> map = toMap(changes);
        assertEquals(QuestStatus.OPEN, map.get(activity.getId()));
    }

    @Test
    @DisplayName("활동형이 LOCKED 상태이고 레벨이 잠기면 LOCKED 유지")
    void activityQuestStaysLockedWhenLevelLocked() {
        idSeq = 1;
        Quest q1 = skillQuest(1, "java", QuestStatus.OPEN);   // Lv1 미완료
        Quest activity = activityQuest(2, QuestStatus.LOCKED);

        var changes = QuestUnlockPolicy.recompute(List.of(q1, activity), List.of());

        Map<Long, QuestStatus> map = toMap(changes);
        assertFalse(map.containsKey(activity.getId()), "LOCKED 유지 → 변경 없음");
    }

    @Test
    @DisplayName("최소 레벨이 1이 아닌 경우에도 최하위 레벨은 열린다")
    void nonStandardMinLevelStillOpens() {
        idSeq = 1;
        Quest q1 = skillQuest(2, "java", QuestStatus.LOCKED);
        Quest q2 = skillQuest(2, "spring", QuestStatus.LOCKED);
        Quest q3 = skillQuest(3, "jpa", QuestStatus.LOCKED);

        var changes = QuestUnlockPolicy.recompute(List.of(q1, q2, q3), List.of());

        Map<Long, QuestStatus> map = toMap(changes);
        assertEquals(QuestStatus.OPEN, map.get(q1.getId()));
        assertEquals(QuestStatus.OPEN, map.get(q2.getId()));
        assertFalse(map.containsKey(q3.getId()));
    }

    @Test
    @DisplayName("연쇄 해금: Lv2 미완료여도 Lv3이 이미 OPEN이면 유지 (단조성)")
    void chainedUnlock_openStaysOpen() {
        idSeq = 1;
        Quest q1 = skillQuest(1, "java", QuestStatus.DONE);
        Quest q2 = skillQuest(1, "spring", QuestStatus.DONE);
        Quest q3 = skillQuest(2, "jpa", QuestStatus.OPEN);      // Lv2 미완료
        Quest q4 = skillQuest(3, "security", QuestStatus.OPEN);  // 이미 OPEN

        var changes = QuestUnlockPolicy.recompute(List.of(q1, q2, q3, q4), List.of());

        Map<Long, QuestStatus> map = toMap(changes);
        assertFalse(map.containsKey(q3.getId()));  // Lv2 열림 유지
        assertFalse(map.containsKey(q4.getId()), "이미 OPEN인 Lv3 퀘스트는 LOCKED로 되돌아가지 않는다");
    }

    // ===== 단조성(monotonic) 테스트 =====

    @Test
    @DisplayName("CUSTOM 퀘스트 추가로 total이 늘어도 이미 OPEN인 상위 레벨은 잠기지 않는다")
    void addCustomQuestDoesNotRelockUpperLevels() {
        idSeq = 1;
        Quest q1 = skillQuest(1, "java", QuestStatus.DONE);
        Quest q2 = skillQuest(1, "spring", QuestStatus.DONE);
        Quest q3 = skillQuest(2, "jpa", QuestStatus.DONE);
        Quest q4 = skillQuest(2, "sql", QuestStatus.DONE);
        Quest q5 = skillQuest(3, "rest", QuestStatus.OPEN);     // Lv3 열려있음
        // Lv2에 CUSTOM 추가 → total 증가, completed < total
        Quest custom = Quest.restore(idSeq++, 1L, null, "axis1",
                2, 2, "Custom Quest", null, null, null,
                QuestSource.CUSTOM, QuestStatus.OPEN, null, 0, Instant.now(), Instant.now());

        var changes = QuestUnlockPolicy.recompute(
                List.of(q1, q2, q3, q4, q5, custom), List.of());

        Map<Long, QuestStatus> map = toMap(changes);
        assertFalse(map.containsKey(q5.getId()),
                "Lv3이 이미 OPEN이면 Lv2에 퀘스트를 추가해도 LOCKED로 되돌아가지 않아야 한다");
    }

    @Test
    @DisplayName("LOCKED→OPEN 방향 해금은 여전히 동작한다")
    void lockedToOpenStillWorks() {
        idSeq = 1;
        Quest q1 = skillQuest(1, "java", QuestStatus.DONE);
        Quest q2 = skillQuest(1, "spring", QuestStatus.DONE);
        Quest q3 = skillQuest(2, "jpa", QuestStatus.LOCKED);

        var changes = QuestUnlockPolicy.recompute(List.of(q1, q2, q3), List.of());

        Map<Long, QuestStatus> map = toMap(changes);
        assertEquals(QuestStatus.OPEN, map.get(q3.getId()), "LOCKED→OPEN 해금은 정상 동작해야 한다");
    }

    @Test
    @DisplayName("연쇄 해금: Lv2 미완료이고 Lv3이 LOCKED이면 LOCKED 유지")
    void chainedUnlock_lockedStaysLocked() {
        idSeq = 1;
        Quest q1 = skillQuest(1, "java", QuestStatus.DONE);
        Quest q2 = skillQuest(1, "spring", QuestStatus.DONE);
        Quest q3 = skillQuest(2, "jpa", QuestStatus.OPEN);      // Lv2 미완료
        Quest q4 = skillQuest(3, "security", QuestStatus.LOCKED);

        var changes = QuestUnlockPolicy.recompute(List.of(q1, q2, q3, q4), List.of());

        Map<Long, QuestStatus> map = toMap(changes);
        assertFalse(map.containsKey(q4.getId()), "Lv3 LOCKED 유지 → 변경 없음");
    }
}
