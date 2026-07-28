package com.myith.core.domain.roadmap;

public enum QuestStatus {
    LOCKED,
    OPEN,
    PENDING,
    DONE,
    ALREADY_KNOWN;

    public boolean isCompleted() {
        return this == DONE || this == ALREADY_KNOWN;
    }

    /**
     * API 응답용 상태명. PENDING은 프론트에 노출하지 않는다(OPEN으로 매핑).
     * DB에는 PENDING을 유지하여 STAR 임시저장 여부를 추적한다.
     */
    public String toApiName() {
        return this == PENDING ? OPEN.name() : this.name();
    }
}
