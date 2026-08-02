package fr.easywork.ingest.pipeline;

import net.sourceforge.tess4j.Tesseract;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises OcrProcessor against a real Tesseract install (no mocking of the OCR
 * engine itself), matching this project's rule against mocking infrastructure in
 * tests that verify it. Requires tesseract-ocr + tesseract-ocr-eng, as installed by
 * backend/Dockerfile and the tests.yml CI workflow.
 */
class OcrProcessorTest {

    private final OcrProcessor ocrProcessor = new OcrProcessor(newTesseract());

    @Test
    void ocr_readsTextFromRenderedImage() throws Exception {
        byte[] pngBytes = renderTextImage("HELLO WORLD");

        String text = ocrProcessor.ocr(pngBytes, "image/png");

        assertThat(text.toUpperCase(java.util.Locale.ROOT)).contains("HELLO");
    }

    @Test
    void ocr_throwsIoException_onCorruptImage() {
        byte[] garbage = "not an image".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> ocrProcessor.ocr(garbage, "image/png"))
            .isInstanceOf(IOException.class);
    }

    @Test
    void ocr_rasterizesEachPdfPage_andConcatenatesText() throws Exception {
        byte[] pdfBytes = buildScannedPdf("PAGE ONE TEXT", "PAGE TWO TEXT");

        String text = ocrProcessor.ocr(pdfBytes, "application/pdf").toUpperCase(java.util.Locale.ROOT);

        assertThat(text).contains("PAGE ONE").contains("PAGE TWO");
    }

    private static Tesseract newTesseract() {
        Tesseract tesseract = new Tesseract();
        // Matches IngestProperties' production default (see application.yml
        // `easywork.ingest.tessdata-path`) — the tesseract-ocr apt package used by
        // backend/Dockerfile and tests.yml CI installs its data here on Ubuntu Jammy.
        String datapath = System.getenv().getOrDefault("TESSDATA_PATH", "/usr/share/tesseract-ocr/4.00/tessdata");
        tesseract.setDatapath(datapath);
        tesseract.setLanguage("eng");
        return tesseract;
    }

    private static byte[] renderTextImage(String text) throws IOException {
        BufferedImage image = new BufferedImage(400, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.BOLD, 32));
        g.drawString(text, 10, 60);
        g.dispose();

        var out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    /** A PDF whose pages are scanned images (no text layer) — the case OcrProcessor exists for. */
    private static byte[] buildScannedPdf(String... textPerPage) throws Exception {
        try (PDDocument document = new PDDocument()) {
            for (String pageText : textPerPage) {
                byte[] pageImageBytes = renderTextImage(pageText);
                var pdImage = org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject.createFromByteArray(
                    document, pageImageBytes, pageText);
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                    contentStream.drawImage(pdImage, 50, 600, pdImage.getWidth() * 1.5f, pdImage.getHeight() * 1.5f);
                }
            }
            var out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }
}
