package fr.easywork.ingest.pipeline;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ContentExtractorTest {

    private final ContentExtractor extractor = new ContentExtractor();

    @Test
    void extract_longPlainText_doesNotRequireOcr() throws Exception {
        String text = "This is a long enough piece of plain text content to clear the native-text threshold.";
        var result = extractor.extract(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));

        assertThat(result.text()).contains("long enough piece of plain text");
        assertThat(result.requiresOcr()).isFalse();
    }

    @Test
    void extract_shortText_requiresOcr() throws Exception {
        var result = extractor.extract(new ByteArrayInputStream("hi".getBytes(StandardCharsets.UTF_8)));

        assertThat(result.requiresOcr()).isTrue();
    }

    @Test
    void extract_pdfWithNativeText_reportsPageCount_andSkipsOcr() throws Exception {
        byte[] pdfBytes = buildPdf(2, "This page has plenty of real extractable text on it, more than fifty characters.");

        var result = extractor.extract(new ByteArrayInputStream(pdfBytes));

        assertThat(result.pageCount()).isEqualTo(2);
        assertThat(result.requiresOcr()).isFalse();
        assertThat(result.text()).contains("real extractable text");
    }

    @Test
    void extract_pdfWithoutTextLayer_requiresOcr_butStillReportsPageCount() throws Exception {
        byte[] pdfBytes = buildBlankPdf(3);

        var result = extractor.extract(new ByteArrayInputStream(pdfBytes));

        assertThat(result.pageCount()).isEqualTo(3);
        assertThat(result.requiresOcr()).isTrue();
    }

    private static byte[] buildPdf(int pageCount, String textPerPage) throws Exception {
        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i < pageCount; i++) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                    contentStream.beginText();
                    contentStream.setFont(PDType1Font.HELVETICA, 12);
                    contentStream.newLineAtOffset(50, 700);
                    contentStream.showText(textPerPage);
                    contentStream.endText();
                }
            }
            var out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private static byte[] buildBlankPdf(int pageCount) throws Exception {
        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i < pageCount; i++) {
                document.addPage(new PDPage());
            }
            var out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }
}
