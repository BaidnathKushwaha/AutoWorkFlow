package com.autoworkflow.scheduler;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;

@Component
public class ResumeAttachmentTextExtractor {

    public String extract(String filename, String mimeType, byte[] data) throws IOException {
        String extension = extension(filename);
        return switch (extension) {
            case "pdf" -> extractPdf(data);
            case "doc" -> extractDoc(data);
            case "docx" -> extractDocx(data);
            default -> throw new IllegalArgumentException("Unsupported resume attachment type: " + filename);
        };
    }

    private String extractPdf(byte[] data) throws IOException {
        try (var document = Loader.loadPDF(data)) {
            String text = new PDFTextStripper().getText(document).trim();
            if (text.isBlank()) throw new IllegalArgumentException("Resume PDF contains no extractable text.");
            return text;
        }
    }

    private String extractDoc(byte[] data) throws IOException {
        try (HWPFDocument document = new HWPFDocument(new ByteArrayInputStream(data));
             WordExtractor extractor = new WordExtractor(document)) {
            String text = extractor.getText().trim();
            if (text.isBlank()) throw new IllegalArgumentException("Resume DOC contains no extractable text.");
            return text;
        }
    }

    private String extractDocx(byte[] data) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(data));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            String text = extractor.getText().trim();
            if (text.isBlank()) throw new IllegalArgumentException("Resume DOCX contains no extractable text.");
            return text;
        }
    }

    private String extension(String filename) {
        if (filename == null || filename.isBlank()) return "";
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
