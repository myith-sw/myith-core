package com.myith.core.application.export;

import com.lowagie.text.pdf.BaseFont;
import org.springframework.stereotype.Component;
import org.xhtmlrenderer.pdf.ITextFontResolver;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;

@Component
public class PdfExportRenderer implements ExportRenderer {

    private static final String FONT_PATH = "/fonts/NotoSansKR-Regular.ttf";

    @Override
    public byte[] render(ExportData data) {
        String html = buildHtml(data);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ITextRenderer renderer = new ITextRenderer();
            ITextFontResolver fontResolver = renderer.getFontResolver();
            fontResolver.addFont(
                    getClass().getResource(FONT_PATH).toExternalForm(),
                    BaseFont.IDENTITY_H, BaseFont.EMBEDDED
            );
            renderer.setDocumentFromString(html);
            renderer.layout();
            renderer.createPDF(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("PDF rendering failed", e);
        }
    }

    @Override
    public String contentType() {
        return "application/pdf";
    }

    @Override
    public String fileExtension() {
        return "pdf";
    }

    private String buildHtml(ExportData data) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN"
                  "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head>
                <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
                <style>
                body { font-family: 'Noto Sans KR', sans-serif; font-size: 11pt; margin: 40px; }
                h1 { font-size: 18pt; margin-bottom: 4px; }
                h2 { font-size: 14pt; margin-top: 20px; border-bottom: 1px solid #ccc; padding-bottom: 4px; }
                .meta { color: #555; margin-bottom: 16px; }
                .quest { margin-bottom: 8px; }
                .quest-title { font-weight: bold; }
                .done { color: #2e7d32; }
                .star { margin-left: 16px; margin-top: 4px; margin-bottom: 12px; padding: 8px; background: #f9f9f9; border-left: 3px solid #1976d2; }
                .star-label { font-weight: bold; color: #1976d2; }
                </style>
                </head>
                <body>
                """);

        sb.append("<h1>").append(esc(data.jobName())).append(" Roadmap</h1>\n");
        sb.append("<div class=\"meta\">");
        if (data.characterNickname() != null) {
            sb.append("Character: ").append(esc(data.characterNickname())).append(" | ");
        }
        sb.append("Stage: ").append(esc(data.stage()))
                .append(" | Completion: ").append(esc(data.completionRate())).append("%");
        sb.append("</div>\n");

        for (ExportData.LevelExport level : data.levels()) {
            sb.append("<h2>Lv.").append(level.level()).append("</h2>\n");
            for (ExportData.QuestExport quest : level.quests()) {
                boolean done = "DONE".equals(quest.status()) || "ALREADY_KNOWN".equals(quest.status());
                sb.append("<div class=\"quest\">");
                if (done) {
                    sb.append("<span class=\"done\">&#x2713;</span> ");
                } else {
                    sb.append("&#x25CB; ");
                }
                sb.append("<span class=\"quest-title\">").append(esc(quest.title())).append("</span>");
                sb.append(" <span style=\"color:#888;\">(").append(esc(quest.axisName())).append(")</span>");
                sb.append("</div>\n");

                if (quest.star() != null) {
                    sb.append("<div class=\"star\">");
                    appendStarField(sb, "Situation", quest.star().situation());
                    appendStarField(sb, "Task", quest.star().task());
                    appendStarField(sb, "Action", quest.star().action());
                    appendStarField(sb, "Result", quest.star().result());
                    sb.append("</div>\n");
                }
            }
        }

        sb.append("</body></html>");
        return sb.toString();
    }

    private void appendStarField(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append("<div><span class=\"star-label\">").append(label).append(":</span> ")
                    .append(esc(value)).append("</div>");
        }
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
