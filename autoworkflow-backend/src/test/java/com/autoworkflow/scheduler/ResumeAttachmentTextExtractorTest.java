package com.autoworkflow.scheduler;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.usermodel.Range;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

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
        byte[] doc;
        try (HWPFDocument document = new HWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Range range = document.getRange();
            range.insertAfter("John Doe Java Backend Developer");
            document.write(output);
            doc = output.toByteArray();
        }

        String text = extractor.extract("john_resume.doc", "application/msword", doc);

        assertTrue(text.contains("John Doe Java Backend Developer"));
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
