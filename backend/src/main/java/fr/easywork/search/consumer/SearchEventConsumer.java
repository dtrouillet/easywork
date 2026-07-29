package fr.easywork.search.consumer;

import fr.easywork.document.event.DocumentDeletedEvent;
import fr.easywork.document.event.DocumentReadyEvent;
import fr.easywork.search.service.SearchIndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@Profile("search")
@RequiredArgsConstructor
public class SearchEventConsumer {

    private final SearchIndexService searchIndexService;

    @ApplicationModuleListener
    public void onDocumentReady(DocumentReadyEvent event) {
        searchIndexService.index(event);
    }

    @ApplicationModuleListener
    public void onDocumentDeleted(DocumentDeletedEvent event) {
        searchIndexService.delete(event.documentId());
    }
}
