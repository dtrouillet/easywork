package fr.easywork.ingest.consumer;

import fr.easywork.document.event.DocumentUploadedEvent;
import fr.easywork.document.event.IngestCompletedEvent;
import fr.easywork.ingest.pipeline.IngestPipeline;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@Profile("ingest")
@RequiredArgsConstructor
public class IngestEventConsumer {

    private final IngestPipeline pipeline;
    private final ApplicationEventPublisher eventPublisher;

    @ApplicationModuleListener
    public void onDocumentUploaded(DocumentUploadedEvent event) {
        IngestCompletedEvent result = pipeline.process(event);
        eventPublisher.publishEvent(result);
    }
}
