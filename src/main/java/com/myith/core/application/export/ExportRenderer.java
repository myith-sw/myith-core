package com.myith.core.application.export;

public interface ExportRenderer {
    byte[] render(ExportData data);
    String contentType();
    String fileExtension();
}
