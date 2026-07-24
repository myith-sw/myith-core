package com.myith.core.application.export;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class MarkdownExportRenderer implements ExportRenderer {

    @Override
    public byte[] render(ExportData data) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(data.jobName()).append(" 로드맵\n\n");
        sb.append("**캐릭터:** ").append(data.characterNickname() != null ? data.characterNickname() : "-").append("\n");
        sb.append("**단계:** ").append(data.stage()).append(" | **완료율:** ").append(data.completionRate()).append("%\n\n");
        sb.append("---\n\n");

        for (ExportData.LevelExport level : data.levels()) {
            sb.append("## Lv.").append(level.level()).append("\n\n");
            for (ExportData.QuestExport quest : level.quests()) {
                String check = quest.status().equals("DONE") || quest.status().equals("ALREADY_KNOWN") ? "x" : " ";
                sb.append("- [").append(check).append("] **").append(quest.title()).append("** (").append(quest.axisName()).append(")\n");

                if (quest.star() != null) {
                    sb.append("\n  > **Situation:** ").append(nullSafe(quest.star().situation())).append("\n");
                    sb.append("  > **Task:** ").append(nullSafe(quest.star().task())).append("\n");
                    sb.append("  > **Action:** ").append(nullSafe(quest.star().action())).append("\n");
                    sb.append("  > **Result:** ").append(nullSafe(quest.star().result())).append("\n\n");
                }
            }
            sb.append("\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String contentType() { return "text/markdown; charset=UTF-8"; }

    @Override
    public String fileExtension() { return "md"; }

    private String nullSafe(String s) { return s != null ? s : "-"; }
}
