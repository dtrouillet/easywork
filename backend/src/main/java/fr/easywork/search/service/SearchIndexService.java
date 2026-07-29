package fr.easywork.search.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.SearchRequest;
import fr.easywork.document.event.DocumentReadyEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Profile("search")
@RequiredArgsConstructor
public class SearchIndexService {

    private static final String INDEX = "documents";

    private final Client client;
    private final ObjectMapper objectMapper;

    public void index(DocumentReadyEvent event) {
        Map<String, Object> doc = Map.of(
            "id", event.documentId().toString(),
            "title", event.title() != null ? event.title() : "",
            "extractedText", event.extractedText() != null ? event.extractedText() : "",
            "mimeType", event.mimeType() != null ? event.mimeType() : "",
            "tags", event.tags() != null ? event.tags() : List.of(),
            "correspondent", event.correspondent() != null ? event.correspondent() : "",
            "documentType", event.documentType() != null ? event.documentType() : "",
            "ownerId", event.ownerId()
        );
        try {
            client.index(INDEX).addDocuments(objectMapper.writeValueAsString(List.of(doc)), "id");
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize document for indexing", e);
        }
    }

    public void delete(UUID documentId) {
        client.index(INDEX).deleteDocument(documentId.toString());
    }

    public Object search(String query, String ownerId, int page, int size) {
        SearchRequest request = SearchRequest.builder()
            .q(query)
            .filter(new String[]{"ownerId = " + ownerId})
            .attributesToRetrieve(new String[]{"id", "title", "mimeType", "tags", "correspondent", "documentType"})
            .hitsPerPage(size)
            .page(page + 1)
            .build();
        return client.index(INDEX).search(request);
    }
}
