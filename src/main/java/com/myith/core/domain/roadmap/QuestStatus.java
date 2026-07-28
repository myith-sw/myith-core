package com.myith.core.domain.roadmap;

public enum QuestStatus {
    LOCKED,
    OPEN,
    PENDING,
    DONE,
    ALREADY_KNOWN;

    /**
     * 레벨 해금 판정용. DONE + ALREADY_KNOWN 모두 포함.
     * 경력자가 상위 레벨을 바로 볼 수 있도록 한다.
     */
    public boolean isCompleted() {
        return this == DONE || this == ALREADY_KNOWN;
    }

    /**
     * 진행률·레이더 집계용. DONE만 포함.
     * ALREADY_KNOWN은 아직 STAR를 작성하지 않았으므로 진행률에서 제외.
     */
    public boolean isDone() {
        return this == DONE;
    }

    /**
     * API 응답용 상태명. PENDING은 프론트에 노출하지 않는다(OPEN으로 매핑).
     * DB에는 PENDING을 유지하여 STAR 임시저장 여부를 추적한다.
     */
    public String toApiName() {
        return this == PENDING ? OPEN.name() : this.name();
    }
}
