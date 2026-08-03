package fr.easywork.ingest.consumer;

import fr.easywork.AbstractIntegrationTest;
import fr.easywork.document.domain.Document;
import fr.easywork.document.domain.DocumentStatus;
import fr.easywork.document.event.DocumentUploadedEvent;
import fr.easywork.document.event.IngestCompletedEvent;
import fr.easywork.document.repository.DocumentRepository;
import fr.easywork.document.service.DocumentService;
import fr.easywork.ingest.pipeline.IngestPipeline;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Proves the full DocumentUploadedEvent -> IngestCompletedEvent chain round-trips through a
 * real broker — externalize (Spring Modulith) -> exchange -> queue ->
 * {@link DocumentUploadedRabbitListener} -> {@link IngestEventConsumer} -> (real, second
 * externalize) -> exchange -> queue -> {@code IngestCompletedRabbitListener} (document/consumer
 * package) -> real {@link DocumentService#onIngestCompleted} — rather than relying on same-JVM
 * {@code ApplicationEventPublisher} delivery, which is all the other ingest tests exercise.
 * {@code ingest} merges with the base class's {@code test} profile, registering the real
 * {@link IngestEventConsumer}/{@link DocumentUploadedRabbitListener} beans; {@link IngestPipeline}
 * is mocked since OCR/Tesseract logic is already covered by IngestPipelineTest — this test is
 * about wiring, not extraction.
 *
 * <p>Deliberately asserts on the document's persisted status rather than stopping at "the
 * pipeline was invoked": an earlier version of this test only checked the first hop and missed
 * that {@link IngestEventConsumer#onDocumentUploaded} republishing {@link IngestCompletedEvent}
 * needs its own active transaction to externalize at all (Modulith's externalization listener is
 * {@code AFTER_COMMIT}-only and silently skips otherwise) — a real bug that a bare
 * {@code @RabbitListener} container thread would have hit in production. Asserting the second
 * hop's real effect (status transition) is what actually catches that class of bug.
 *
 * <p>Modulith's externalization listener only fires {@code AFTER_COMMIT} — publishing the event
 * outside of an active, committed transaction makes it silently skip externalization (matches
 * {@code DocumentService.upload()}, which is {@code @Transactional}), so a
 * {@link TransactionTemplate} is used here instead of {@code @Transactional} on the test itself
 * (Spring's test-managed transactions roll back rather than commit, which would skip
 * externalization the same way).
 */
@ActiveProfiles("ingest")
class DocumentUploadedAmqpRoundTripIntegrationTest extends AbstractIntegrationTest {

    @Autowired ApplicationEventPublisher eventPublisher;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired DocumentRepository documentRepository;
    @MockitoBean IngestPipeline pipeline;

    private static final String OWNER = "amqp-roundtrip-user";

    @Test
    void documentUploadedEvent_reachesReady_viaRealAmqpBrokerBothHops() {
        Document doc = new Document();
        doc.setTitle("AMQP round trip");
        doc.setOriginalFilename("amqp-round-trip.txt");
        doc.setMimeType("text/plain");
        doc.setOwnerId(OWNER);
        doc.setStatus(DocumentStatus.RECEIVED);
        transactionTemplate.executeWithoutResult(status -> documentRepository.save(doc));
        UUID documentId = doc.getId();

        var uploaded = new DocumentUploadedEvent(documentId, "key", "text/plain", OWNER);
        var completed = new IngestCompletedEvent(
            documentId, "hash", "extracted text well over the threshold", 1, false, true, null, List.of());
        when(pipeline.process(any())).thenReturn(completed);

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(uploaded));

        DocumentStatus finalStatus = awaitTerminalStatus(documentId);
        assertThat(finalStatus).isEqualTo(DocumentStatus.READY);
    }

    private DocumentStatus awaitTerminalStatus(UUID documentId) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        DocumentStatus status;
        do {
            status = documentRepository.findById(documentId).orElseThrow().getStatus();
            if (status == DocumentStatus.READY || status == DocumentStatus.FAILED) {
                return status;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        } while (Instant.now().isBefore(deadline));
        return status;
    }
}
