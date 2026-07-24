package com.myith.core.application.export;

import java.util.List;

public record ExportData(
        String jobName,
        String characterNickname,
        String stage,
        String completionRate,
        List<LevelExport> levels
) {
    public record LevelExport(int level, List<QuestExport> quests) {}

    public record QuestExport(String title, String axisName, String status,
                              StarExport star) {}

    public record StarExport(String situation, String task, String action, String result) {}
}
