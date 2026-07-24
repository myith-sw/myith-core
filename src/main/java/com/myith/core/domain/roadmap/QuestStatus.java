package com.myith.core.domain.roadmap;

public enum QuestStatus {
    LOCKED,
    OPEN,
    DONE,
    ALREADY_KNOWN;

    public boolean isCompleted() {
        return this == DONE || this == ALREADY_KNOWN;
    }
}
