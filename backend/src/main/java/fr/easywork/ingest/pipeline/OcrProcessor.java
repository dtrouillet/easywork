package fr.easywork.ingest.pipeline;

import net.sourceforge.tess4j.Tesseract;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.InputStream;

import javax.imageio.ImageIO;

@Component
@Profile("ingest")
class OcrProcessor {

    private final Tesseract tesseract;

    OcrProcessor(Tesseract tesseract) {
        this.tesseract = tesseract;
    }

    String ocr(InputStream input) throws Exception {
        BufferedImage image = ImageIO.read(input);
        return tesseract.doOCR(image);
    }
}
