package com.myith.core.domain.star;

import java.time.Instant;
import java.util.List;

public class StarRecord {

    private Long id;
    private Long questId;
    private Long userId;
    private String situation;
    private String task;
    private String action;
    private String result;
    private String completeness;
    private List<String> tags;
    private Instant createdAt;
    private Instant updatedAt;

    private StarRecord() {}

    public static StarRecord create(Long questId, Long userId,
                                    String situation, String task, String action, String result) {
        StarRecord r = new StarRecord();
        r.questId = questId;
        r.userId = userId;
        r.situation = situation;
        r.task = task;
        r.action = action;
        r.result = result;
        r.completeness = calculateCompleteness(situation, task, action, result);
        r.createdAt = Instant.now();
        r.updatedAt = r.createdAt;
        return r;
    }

    public void update(String situation, String task, String action, String result) {
        this.situation = situation;
        this.task = task;
        this.action = action;
        this.result = result;
        this.completeness = calculateCompleteness(situation, task, action, result);
        this.updatedAt = Instant.now();
    }

    private static String calculateCompleteness(String s, String t, String a, String r) {
        int filled = 0;
        if (s != null && !s.isBlank()) filled++;
        if (t != null && !t.isBlank()) filled++;
        if (a != null && !a.isBlank()) filled++;
        if (r != null && !r.isBlank()) filled++;
        return switch (filled) {
            case 4 -> "COMPLETE";
            case 0 -> "EMPTY";
            default -> "PARTIAL";
        };
    }

    public static StarRecord restore(Long id, Long questId, Long userId,
                                     String situation, String task, String action, String result,
                                     String completeness, List<String> tags,
                                     Instant createdAt, Instant updatedAt) {
        StarRecord r = new StarRecord();
        r.id = id;
        r.questId = questId;
        r.userId = userId;
        r.situation = situation;
        r.task = task;
        r.action = action;
        r.result = result;
        r.completeness = completeness;
        r.tags = tags;
        r.createdAt = createdAt;
        r.updatedAt = updatedAt;
        return r;
    }

    public Long getId() { return id; }
    public Long getQuestId() { return questId; }
    public Long getUserId() { return userId; }
    public String getSituation() { return situation; }
    public String getTask() { return task; }
    public String getAction() { return action; }
    public String getResult() { return result; }
    public String getCompleteness() { return completeness; }
    public List<String> getTags() { return tags; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setId(Long id) { this.id = id; }
}
