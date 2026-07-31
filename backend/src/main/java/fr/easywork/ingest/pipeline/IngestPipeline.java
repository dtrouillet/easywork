package fr.easywork.ingest.pipeline;

import fr.easywork.document.DocumentStorage;
import fr.easywork.document.event.DocumentUploadedEvent;
import fr.easywork.document.event.IngestCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
@Profile("ingest")
@RequiredArgsConstructor
public class IngestPipeline {

    private final DocumentStorage storageService;
    private final ContentExtractor extractor;
    private final OcrProcessor ocrProcessor;

    public IngestCompletedEvent process(DocumentUploadedEvent event) {
        try {
            byte[] fileBytes;
            try (InputStream is = storageService.download(event.storageKey())) {
                fileBytes = is.readAllBytes();
            }

            String contentHash = sha256(fileBytes);

            ExtractionResult extraction = extractor.extract(
                new java.io.ByteArrayInputStream(fileBytes));

            String text = extraction.text();
            boolean ocrApplied = false;

            if (extraction.requiresOcr()) {
                text = ocrProcessor.ocr(new java.io.ByteArrayInputStream(fileBytes));
                ocrApplied = true;
            }

            return new IngestCompletedEvent(
                event.documentId(), contentHash, text, null, ocrApplied, true, null);

        } catch (Exception e) {
            return new IngestCompletedEvent(
                event.documentId(), null, null, null, false, false, e.getMessage());
        }
    }

    private String sha256(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(data));
    }
}
