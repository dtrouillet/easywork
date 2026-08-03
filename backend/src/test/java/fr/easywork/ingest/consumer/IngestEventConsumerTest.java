package fr.easywork.ingest.consumer;

import fr.easywork.document.event.DocumentUploadedEvent;
import fr.easywork.document.event.IngestCompletedEvent;
import fr.easywork.ingest.pipeline.IngestPipeline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngestEventConsumerTest {

    @Mock IngestPipeline pipeline;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks IngestEventConsumer consumer;

    @Test
    void onDocumentUploaded_delegatesToPipeline_andRepublishesResult() {
        UUID docId = UUID.randomUUID();
        var uploaded = new DocumentUploadedEvent(docId, "key", "application/pdf", "user1");
        var completed = new IngestCompletedEvent(docId, "hash", "text", 1, false, true, null, List.of());
        when(pipeline.process(uploaded)).thenReturn(completed);

        consumer.onDocumentUploaded(uploaded);

        verify(pipeline).process(uploaded);
        verify(eventPublisher).publishEvent(completed);
    }

    @Test
    void onDocumentUploaded_republishesFailureEvent_whenPipelineFails() {
        UUID docId = UUID.randomUUID();
        var uploaded = new DocumentUploadedEvent(docId, "key", "application/pdf", "user1");
        var failed = new IngestCompletedEvent(docId, null, null, null, false, false, "boom", List.of());
        when(pipeline.process(uploaded)).thenReturn(failed);

        consumer.onDocumentUploaded(uploaded);

        verify(eventPublisher).publishEvent(failed);
    }
}
