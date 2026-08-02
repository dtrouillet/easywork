package fr.easywork.ingest.pipeline;

import fr.easywork.document.DocumentDuplicateCheck;
import fr.easywork.document.DocumentStorage;
import fr.easywork.document.event.DocumentUploadedEvent;
import fr.easywork.document.event.IngestCompletedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngestPipelineTest {

    @Mock DocumentStorage storageService;
    @Mock DocumentDuplicateCheck duplicateCheck;
    @Mock ContentExtractor extractor;
    @Mock OcrProcessor ocrProcessor;

    private IngestPipeline pipeline;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        pipeline = new IngestPipeline(storageService, duplicateCheck, extractor, ocrProcessor);
    }

    private static final byte[] FILE_BYTES = "hello world".getBytes(StandardCharsets.UTF_8);

    @Test
    void process_shortCircuits_whenDuplicateFound_neverTouchingExtractionOrOcr() throws Exception {
        UUID docId = UUID.randomUUID();
        var event = new DocumentUploadedEvent(docId, "key", "application/pdf", "user1");
        when(storageService.download("key")).thenReturn(new ByteArrayInputStream(FILE_BYTES));
        when(duplicateCheck.existsDuplicate(sha256(FILE_BYTES), "user1", docId)).thenReturn(true);

        IngestCompletedEvent result = pipeline.process(event);

        assertThat(result.success()).isTrue();
        assertThat(result.contentHash()).isEqualTo(sha256(FILE_BYTES));
        assertThat(result.extractedText()).isNull();
        verifyNoInteractions(extractor);
        verifyNoInteractions(ocrProcessor);
    }

    @Test
    void process_usesNativeText_whenOcrNotRequired() throws Exception {
        UUID docId = UUID.randomUUID();
        var event = new DocumentUploadedEvent(docId, "key", "text/plain", "user1");
        when(storageService.download("key")).thenReturn(new ByteArrayInputStream(FILE_BYTES));
        when(duplicateCheck.existsDuplicate(anyString(), anyString(), any())).thenReturn(false);
        when(extractor.extract(any())).thenReturn(new ExtractionResult("hello world", "text/plain", false, 1));

        IngestCompletedEvent result = pipeline.process(event);

        assertThat(result.success()).isTrue();
        assertThat(result.extractedText()).isEqualTo("hello world");
        assertThat(result.ocrApplied()).isFalse();
        assertThat(result.pageCount()).isEqualTo(1);
        verify(ocrProcessor, never()).ocr(any(), anyString());
    }

    @Test
    void process_runsOcr_whenExtractionRequiresIt() throws Exception {
        UUID docId = UUID.randomUUID();
        var event = new DocumentUploadedEvent(docId, "key", "application/pdf", "user1");
        when(storageService.download("key")).thenReturn(new ByteArrayInputStream(FILE_BYTES));
        when(duplicateCheck.existsDuplicate(anyString(), anyString(), any())).thenReturn(false);
        when(extractor.extract(any())).thenReturn(new ExtractionResult("", "application/pdf", true, 3));
        when(ocrProcessor.ocr(FILE_BYTES, "application/pdf")).thenReturn("scanned text");

        IngestCompletedEvent result = pipeline.process(event);

        assertThat(result.success()).isTrue();
        assertThat(result.extractedText()).isEqualTo("scanned text");
        assertThat(result.ocrApplied()).isTrue();
        assertThat(result.pageCount()).isEqualTo(3);
    }

    @Test
    void process_returnsFailureEvent_whenExtractionThrows() throws Exception {
        UUID docId = UUID.randomUUID();
        var event = new DocumentUploadedEvent(docId, "key", "application/pdf", "user1");
        when(storageService.download("key")).thenReturn(new ByteArrayInputStream(FILE_BYTES));
        when(duplicateCheck.existsDuplicate(anyString(), anyString(), any())).thenReturn(false);
        when(extractor.extract(any())).thenThrow(new RuntimeException("Tika blew up"));

        IngestCompletedEvent result = pipeline.process(event);

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).isEqualTo("Tika blew up");
        assertThat(result.documentId()).isEqualTo(docId);
    }

    private static String sha256(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(data));
    }
}
