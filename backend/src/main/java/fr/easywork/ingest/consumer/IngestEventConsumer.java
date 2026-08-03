package fr.easywork.ingest.consumer;

import fr.easywork.document.event.DocumentUploadedEvent;
import fr.easywork.document.event.IngestCompletedEvent;
import fr.easywork.ingest.pipeline.IngestPipeline;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("ingest")
@RequiredArgsConstructor
public class IngestEventConsumer {

    private final IngestPipeline pipeline;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Invoked by {@link DocumentUploadedRabbitListener} — see its javadoc for why AMQP-only.
     * {@code @Transactional} here isn't about any database work of its own (there is none) — it
     * exists so the {@code eventPublisher.publishEvent(result)} below has an active transaction
     * to commit, which is what {@code IngestCompletedEvent}'s {@code @Externalized} AMQP
     * publish requires to fire at all: Modulith's externalization listener only runs
     * {@code AFTER_COMMIT} and silently skips publishing if no transaction is active — exactly
     * what would happen calling this from a bare {@code @RabbitListener} container thread.
     */
    @Transactional
    public void onDocumentUploaded(DocumentUploadedEvent event) {
        IngestCompletedEvent result = pipeline.process(event);
        eventPublisher.publishEvent(result);
    }
}
