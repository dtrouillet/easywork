package fr.easywork.document.service;

import fr.easywork.document.domain.Document;
import fr.easywork.document.domain.DocumentStatus;
import fr.easywork.document.dto.DocumentSearchCriteria;
import fr.easywork.document.dto.DocumentUpdateRequest;
import fr.easywork.document.event.DocumentDeletedEvent;
import fr.easywork.document.event.DocumentReadyEvent;
import fr.easywork.document.event.IngestCompletedEvent;
import fr.easywork.document.exception.DocumentNotFoundException;
import fr.easywork.document.exception.DuplicateDocumentException;
import fr.easywork.document.mapper.DocumentMapper;
import fr.easywork.document.repository.CorrespondentRepository;
import fr.easywork.document.repository.DocumentRepository;
import fr.easywork.document.repository.DocumentTypeRepository;
import fr.easywork.document.repository.TagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock DocumentRepository documentRepository;
    @Mock TagRepository tagRepository;
    @Mock CorrespondentRepository correspondentRepository;
    @Mock DocumentTypeRepository documentTypeRepository;
    @Mock StorageService storageService;
    @Mock DocumentMapper mapper;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks DocumentService documentService;

    // --- list ---

    @Test
    void list_delegatesToSpecification() {
        var doc = new Document();
        var page = new PageImpl<>(List.of(doc));
        when(documentRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(mapper.toDto(doc)).thenReturn(null);

        var criteria = new DocumentSearchCriteria(null, null, null, null, null);
        var result = documentService.list("user1", criteria, PageRequest.of(0, 25));

        assertThat(result.content()).hasSize(1);
        verify(documentRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    // --- get ---

    @Test
    void get_throwsNotFound_whenDocumentMissing() {
        UUID id = UUID.randomUUID();
        when(documentRepository.findByIdAndOwnerId(id, "user1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.get(id, "user1"))
            .isInstanceOf(DocumentNotFoundException.class);
    }

    // --- update ---

    @Test
    void update_changesTitleAndPublishesReindexEvent_whenReady() {
        UUID id = UUID.randomUUID();
        Document doc = readyDocument(id, "user1");
        when(documentRepository.findByIdAndOwnerId(id, "user1")).thenReturn(Optional.of(doc));
        when(documentRepository.save(any())).thenReturn(doc);

        documentService.update(id, "user1", new DocumentUpdateRequest("New Title", null, null, null, null));

        assertThat(doc.getTitle()).isEqualTo("New Title");
        verify(eventPublisher).publishEvent(any(DocumentReadyEvent.class));
    }

    @Test
    void update_doesNotPublishEvent_whenReceived() {
        UUID id = UUID.randomUUID();
        Document doc = new Document();
        doc.setId(id);
        doc.setStatus(DocumentStatus.RECEIVED);
        when(documentRepository.findByIdAndOwnerId(id, "user1")).thenReturn(Optional.of(doc));
        when(documentRepository.save(any())).thenReturn(doc);

        documentService.update(id, "user1", new DocumentUpdateRequest("New Title", null, null, null, null));

        verify(eventPublisher, never()).publishEvent(any());
    }

    // --- onIngestCompleted ---

    @Test
    void onIngestCompleted_marksDocumentReady_onSuccess() {
        UUID id = UUID.randomUUID();
        Document doc = new Document();
        doc.setId(id);
        doc.setOwnerId("user1");
        doc.setStatus(DocumentStatus.RECEIVED);
        doc.setStorageKey("key");

        when(documentRepository.findById(id)).thenReturn(Optional.of(doc));
        when(documentRepository.findByContentHashAndOwnerId("hash", "user1")).thenReturn(Optional.empty());
        when(documentRepository.save(any())).thenReturn(doc);

        documentService.onIngestCompleted(new IngestCompletedEvent(id, "hash", "text", 2, false, true, null));

        verify(documentRepository).save(argThat(d -> d.getStatus() == DocumentStatus.READY));
        verify(eventPublisher).publishEvent(any(DocumentReadyEvent.class));
    }

    @Test
    void onIngestCompleted_deletesDuplicate_andThrows() {
        UUID id = UUID.randomUUID();
        UUID existingId = UUID.randomUUID();

        Document doc = new Document();
        doc.setId(id);
        doc.setOwnerId("user1");
        doc.setStorageKey("key");
        doc.setStatus(DocumentStatus.RECEIVED);

        Document existing = new Document();
        existing.setId(existingId);

        when(documentRepository.findById(id)).thenReturn(Optional.of(doc));
        when(documentRepository.findByContentHashAndOwnerId("hash", "user1"))
            .thenReturn(Optional.of(existing));

        assertThatThrownBy(() ->
            documentService.onIngestCompleted(new IngestCompletedEvent(id, "hash", "text", 1, false, true, null)))
            .isInstanceOf(DuplicateDocumentException.class);

        verify(storageService).delete("key");
        verify(documentRepository).delete(doc);
    }

    // --- lifecycle transitions ---

    @Test
    void trash_transitionsToTrash() {
        UUID id = UUID.randomUUID();
        Document doc = readyDocument(id, "user1");
        when(documentRepository.findByIdAndOwnerId(id, "user1")).thenReturn(Optional.of(doc));
        when(documentRepository.save(any())).thenReturn(doc);

        documentService.trash(id, "user1");

        verify(documentRepository).save(argThat(d -> d.getStatus() == DocumentStatus.TRASH));
    }

    @Test
    void archive_transitionsToArchived() {
        UUID id = UUID.randomUUID();
        Document doc = readyDocument(id, "user1");
        when(documentRepository.findByIdAndOwnerId(id, "user1")).thenReturn(Optional.of(doc));
        when(documentRepository.save(any())).thenReturn(doc);

        documentService.archive(id, "user1");

        verify(documentRepository).save(argThat(d -> d.getStatus() == DocumentStatus.ARCHIVED));
    }

    // --- permanentDelete ---

    @Test
    void permanentDelete_scrubsPersonalData_deletesRowAndPublishesEvent() {
        UUID id = UUID.randomUUID();
        Document doc = new Document();
        doc.setId(id);
        doc.setStatus(DocumentStatus.TRASH);
        doc.setStorageKey("key");
        doc.setTitle("Sensitive Title");
        doc.setOwnerId("user1");

        when(documentRepository.findByIdAndOwnerId(id, "user1")).thenReturn(Optional.of(doc));
        when(documentRepository.save(any())).thenReturn(doc);

        documentService.permanentDelete(id, "user1");

        // Personal data scrubbed
        assertThat(doc.getTitle()).isEqualTo("[supprimé]");
        assertThat(doc.getOwnerId()).isEqualTo("[supprimé]");

        // Real DB deletion
        verify(documentRepository).delete(doc);

        // MinIO + search event
        verify(storageService).delete("key");
        verify(eventPublisher).publishEvent(any(DocumentDeletedEvent.class));
    }

    @Test
    void permanentDelete_skipsStorageDelete_whenNoStorageKey() {
        UUID id = UUID.randomUUID();
        Document doc = new Document();
        doc.setId(id);
        doc.setStatus(DocumentStatus.TRASH);
        doc.setOwnerId("user1");

        when(documentRepository.findByIdAndOwnerId(id, "user1")).thenReturn(Optional.of(doc));
        when(documentRepository.save(any())).thenReturn(doc);

        documentService.permanentDelete(id, "user1");

        verify(storageService, never()).delete(any());
        verify(documentRepository).delete(doc);
    }

    // --- helpers ---

    private static Document readyDocument(UUID id, String ownerId) {
        Document doc = new Document();
        doc.setId(id);
        doc.setOwnerId(ownerId);
        doc.setStatus(DocumentStatus.READY);
        return doc;
    }
}
