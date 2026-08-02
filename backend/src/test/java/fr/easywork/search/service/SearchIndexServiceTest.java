package fr.easywork.search.service;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.SearchRequest;
import fr.easywork.document.event.DocumentReadyEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchIndexServiceTest {

    @Mock Client client;
    @Mock Index index;

    @Test
    void index_sendsDocumentFieldsAsJson() {
        var service = new SearchIndexService(client, JsonMapper.builder().build());
        when(client.index("documents")).thenReturn(index);
        UUID id = UUID.randomUUID();
        var event = new DocumentReadyEvent(
            id, "Invoice", "some extracted text", "application/pdf", LocalDate.of(2026, 1, 1),
            List.of("energy", "edf"), "EDF", "Invoice", "user1");

        service.index(event);

        var jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(index).addDocuments(jsonCaptor.capture(), eq("id"));
        String json = jsonCaptor.getValue();
        assertThat(json).contains(id.toString())
            .contains("Invoice")
            .contains("some extracted text")
            .contains("application/pdf")
            .contains("energy")
            .contains("EDF")
            .contains("user1");
    }

    @Test
    void index_substitutesEmptyDefaults_whenOptionalFieldsAreNull() {
        var service = new SearchIndexService(client, JsonMapper.builder().build());
        when(client.index("documents")).thenReturn(index);
        UUID id = UUID.randomUUID();
        var event = new DocumentReadyEvent(id, null, null, null, null, null, null, null, "user1");

        service.index(event);

        var jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(index).addDocuments(jsonCaptor.capture(), eq("id"));
        assertThat(jsonCaptor.getValue()).doesNotContain("null");
    }

    @Test
    void delete_removesDocumentByIdFromIndex() {
        var service = new SearchIndexService(client, JsonMapper.builder().build());
        when(client.index("documents")).thenReturn(index);
        UUID id = UUID.randomUUID();

        service.delete(id);

        verify(index).deleteDocument(id.toString());
    }

    @Test
    void search_scopesToOwnerAndAppliesPagination() {
        var service = new SearchIndexService(client, JsonMapper.builder().build());
        when(client.index("documents")).thenReturn(index);

        service.search("invoice", "user1", 2, 10);

        var requestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(index).search(requestCaptor.capture());
        SearchRequest request = requestCaptor.getValue();
        assertThat(request.getQ()).isEqualTo("invoice");
        assertThat(request.getFilter()).containsExactly("ownerId = user1");
        assertThat(request.getPage()).isEqualTo(3);
        assertThat(request.getHitsPerPage()).isEqualTo(10);
    }
}
