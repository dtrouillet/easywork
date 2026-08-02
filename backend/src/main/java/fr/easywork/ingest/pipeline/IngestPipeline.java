package fr.easywork.ingest.pipeline;

import fr.easywork.document.DocumentDuplicateCheck;
import fr.easywork.document.DocumentStorage;
import fr.easywork.document.event.DocumentUploadedEvent;
import fr.easywork.document.event.IngestCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
@Profile("ingest")
@RequiredArgsConstructor
public class IngestPipeline {

    private final DocumentStorage storageService;
    private final DocumentDuplicateCheck duplicateCheck;
    private final ContentExtractor extractor;
    private final OcrProcessor ocrProcessor;

    public IngestCompletedEvent process(DocumentUploadedEvent event) {
        try {
            byte[] fileBytes;
            try (InputStream is = storageService.download(event.storageKey())) {
                fileBytes = is.readAllBytes();
            }

            String contentHash = sha256(fileBytes);

            // Duplicate detection before processing: skip extraction/OCR entirely for a
            // file that already exists — DocumentService.onIngestCompleted() still runs
            // the authoritative delete-and-reject flow once this event comes back.
            if (duplicateCheck.existsDuplicate(contentHash, event.ownerId(), event.documentId())) {
                return new IngestCompletedEvent(
                    event.documentId(), contentHash, null, null, false, true, null);
            }

            ExtractionResult extraction = extractor.extract(new ByteArrayInputStream(fileBytes));

            String text = extraction.text();
            boolean ocrApplied = false;

            if (extraction.requiresOcr()) {
                text = ocrProcessor.ocr(fileBytes, event.mimeType());
                ocrApplied = true;
            }

            return new IngestCompletedEvent(
                event.documentId(), contentHash, text, extraction.pageCount(), ocrApplied, true, null);

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
