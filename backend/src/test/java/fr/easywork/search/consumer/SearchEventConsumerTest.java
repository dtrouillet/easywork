package fr.easywork.search.consumer;

import fr.easywork.document.event.DocumentDeletedEvent;
import fr.easywork.document.event.DocumentReadyEvent;
import fr.easywork.search.service.SearchIndexService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SearchEventConsumerTest {

    @Mock SearchIndexService searchIndexService;
    @InjectMocks SearchEventConsumer consumer;

    @Test
    void onDocumentReady_indexesTheDocument() {
        var event = new DocumentReadyEvent(
            UUID.randomUUID(), "Invoice", "text", "application/pdf", null, List.of(), null, null, "user1");

        consumer.onDocumentReady(event);

        verify(searchIndexService).index(event);
    }

    @Test
    void onDocumentDeleted_removesFromIndex() {
        UUID id = UUID.randomUUID();
        var event = new DocumentDeletedEvent(id);

        consumer.onDocumentDeleted(event);

        verify(searchIndexService).delete(id);
    }
}
