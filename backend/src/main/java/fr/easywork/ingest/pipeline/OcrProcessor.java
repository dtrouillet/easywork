package fr.easywork.ingest.pipeline;

import net.sourceforge.tess4j.Tesseract;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

@Component
@Profile("ingest")
class OcrProcessor {

    private static final String PDF_MIME_TYPE = "application/pdf";
    private static final int OCR_RENDER_DPI = 300;

    private final Tesseract tesseract;

    OcrProcessor(Tesseract tesseract) {
        this.tesseract = tesseract;
    }

    String ocr(byte[] fileBytes, String mimeType) throws Exception {
        if (PDF_MIME_TYPE.equals(mimeType)) {
            return ocrPdf(fileBytes);
        }
        return ocrImage(fileBytes);
    }

    private String ocrImage(byte[] fileBytes) throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(fileBytes));
        if (image == null) {
            throw new IOException("Unsupported or corrupt image content for OCR");
        }
        return tesseract.doOCR(image);
    }

    private String ocrPdf(byte[] fileBytes) throws Exception {
        try (PDDocument document = PDDocument.load(fileBytes)) {
            PDFRenderer renderer = new PDFRenderer(document);
            StringBuilder text = new StringBuilder();
            int pageCount = document.getNumberOfPages();
            for (int page = 0; page < pageCount; page++) {
                BufferedImage image = renderer.renderImageWithDPI(page, OCR_RENDER_DPI, ImageType.GRAY);
                text.append(tesseract.doOCR(image));
                if (page < pageCount - 1) {
                    text.append('\n');
                }
            }
            return text.toString();
        }
    }
}
