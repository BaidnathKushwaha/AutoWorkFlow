package com.autoworkflow.scheduler;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;

class ResumeAttachmentTextExtractorTest {
    private final ResumeAttachmentTextExtractor extractor = new ResumeAttachmentTextExtractor();

    @Test
    void extractsPdfText() throws Exception {
        byte[] pdf = createPdf("John Doe\nJava Spring Boot Developer");

        String text = extractor.extract("john_resume.pdf", "application/pdf", pdf);

        assertTrue(text.contains("John Doe"));
        assertTrue(text.contains("Java Spring Boot Developer"));
    }

    @Test
    void extractsDocxText() throws Exception {
        byte[] docx;
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("John Doe Java Backend Developer");
            document.write(output);
            docx = output.toByteArray();
        }

        String text = extractor.extract("john_resume.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx);

        assertTrue(text.contains("John Doe Java Backend Developer"));
    }

    @Test
    void extractsDocText() throws Exception {
        byte[] doc = sampleDocBytes();

        String text = extractor.extract("john_resume.doc", "application/msword", doc);

        assertTrue(text.contains("John Doe"));
        assertTrue(text.contains("Java Backend Developer"));
        assertTrue(text.contains("Spring Boot PostgreSQL"));
    }

    @Test
    void rejectsUnsupportedAttachmentType() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> extractor.extract("profile.png", "image/png", new byte[]{1, 2, 3}));

        assertTrue(error.getMessage().contains("Unsupported resume attachment type"));
    }

    @Test
    void rejectsEmptyPdfText() throws Exception {
        byte[] pdf = createPdf("");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> extractor.extract("empty.pdf", "application/pdf", pdf));

        assertTrue(error.getMessage().contains("contains no extractable text"));
    }

    private byte[] sampleDocBytes() throws IOException {
        String gzipBase64 = "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";
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(gzipBase64)));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            gzip.transferTo(output);
            return output.toByteArray();
        }
    }

    private byte[] createPdf(String text) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            if (!text.isBlank()) {
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(50, 700);
                    for (String line : text.split("\\n")) {
                        content.showText(line);
                        content.newLineAtOffset(0, -18);
                    }
                    content.endText();
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
