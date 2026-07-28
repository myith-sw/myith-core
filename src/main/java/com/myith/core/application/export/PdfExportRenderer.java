package com.myith.core.application.export;

import com.lowagie.text.pdf.BaseFont;
import org.springframework.stereotype.Component;
import org.xhtmlrenderer.pdf.ITextFontResolver;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Component
public class PdfExportRenderer implements ExportRenderer {

    private static final String FONT_PATH = "/fonts/NotoSansKR-Regular.ttf";
    private static final String FONT_FAMILY = "Noto Sans KR";

    private volatile String fontFilePath;

    @Override
    public byte[] render(ExportData data) {
        String fontPath = ensureFontFile();
        String html = buildHtml(data, fontPath);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ITextRenderer renderer = new ITextRenderer();
            ITextFontResolver fontResolver = renderer.getFontResolver();
            fontResolver.addFont(fontPath, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            renderer.setDocumentFromString(html);
            renderer.layout();
            renderer.createPDF(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("PDF rendering failed", e);
        }
    }

    /**
     * classpath의 폰트를 임시 파일로 추출한다.
     * Flying Saucer의 addFont()는 jar: URL을 처리하지 못하므로 실제 파일 경로가 필요하다.
     */
    private String ensureFontFile() {
        if (fontFilePath != null) return fontFilePath;
        synchronized (this) {
            if (fontFilePath != null) return fontFilePath;
            try (InputStream is = getClass().getResourceAsStream(FONT_PATH)) {
                if (is == null) {
                    throw new IllegalStateException("Korean font not found on classpath: " + FONT_PATH);
                }
                Path tempFile = Files.createTempFile("NotoSansKR", ".ttf");
                Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
                tempFile.toFile().deleteOnExit();
                fontFilePath = tempFile.toAbsolutePath().toString();
                return fontFilePath;
            } catch (IllegalStateException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException("Failed to extract font file", e);
            }
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

    private String buildHtml(ExportData data, String fontPath) {
        String fontUrl = "file:///" + fontPath.replace("\\", "/");
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\"\n");
        sb.append("  \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\n");
        sb.append("<html xmlns=\"http://www.w3.org/1999/xhtml\">\n");
        sb.append("<head>\n");
        sb.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"/>\n");
        sb.append("<style>\n");
        // @font-face with -fs-font-metric-src is required for Flying Saucer CJK rendering
        sb.append("@font-face {\n");
        sb.append("  font-family: '").append(FONT_FAMILY).append("';\n");
        sb.append("  src: url('").append(fontUrl).append("');\n");
        sb.append("  -fs-font-metric-src: url('").append(fontUrl).append("');\n");
        sb.append("}\n");
        sb.append("body { font-family: '").append(FONT_FAMILY).append("', sans-serif; font-size: 11pt; margin: 40px; }\n");
        sb.append("h1 { font-size: 18pt; margin-bottom: 4px; }\n");
        sb.append("h2 { font-size: 14pt; margin-top: 20px; border-bottom: 1px solid #ccc; padding-bottom: 4px; }\n");
        sb.append(".meta { color: #555; margin-bottom: 16px; }\n");
        sb.append(".quest { margin-bottom: 8px; }\n");
        sb.append(".quest-title { font-weight: bold; }\n");
        sb.append(".done { color: #2e7d32; }\n");
        sb.append(".star { margin-left: 16px; margin-top: 4px; margin-bottom: 12px; padding: 8px; background: #f9f9f9; border-left: 3px solid #1976d2; }\n");
        sb.append(".star-label { font-weight: bold; color: #1976d2; }\n");
        sb.append("</style>\n");
        sb.append("</head>\n");
        sb.append("<body>\n");

        sb.append("<h1>").append(esc(data.jobName())).append(" 로드맵</h1>\n");
        sb.append("<div class=\"meta\">");
        if (data.characterNickname() != null) {
            sb.append("캐릭터: ").append(esc(data.characterNickname())).append(" | ");
        }
        sb.append("단계: ").append(esc(data.stage()))
                .append(" | 완료율: ").append(esc(data.completionRate())).append("%");
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
                    appendStarField(sb, "S", quest.star().situation());
                    appendStarField(sb, "T", quest.star().task());
                    appendStarField(sb, "A", quest.star().action());
                    appendStarField(sb, "R", quest.star().result());
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
