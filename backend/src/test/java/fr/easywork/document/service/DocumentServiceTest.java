package fr.easywork.document.service;

import fr.easywork.document.domain.Document;
import fr.easywork.document.domain.DocumentStatus;
import fr.easywork.document.event.IngestCompletedEvent;
import fr.easywork.document.exception.DocumentNotFoundException;
import fr.easywork.document.exception.DuplicateDocumentException;
import fr.easywork.document.mapper.DocumentMapper;
import fr.easywork.document.repository.DocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock DocumentRepository documentRepository;
    @Mock StorageService storageService;
    @Mock DocumentMapper mapper;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks DocumentService documentService;

    @Test
    void get_throwsNotFound_whenDocumentMissing() {
        UUID id = UUID.randomUUID();
        when(documentRepository.findByIdAndOwnerId(id, "user1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.get(id, "user1"))
            .isInstanceOf(DocumentNotFoundException.class);
    }

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

        IngestCompletedEvent event = new IngestCompletedEvent(id, "hash", "text", 2, false, true, null);
        documentService.onIngestCompleted(event);

        verify(documentRepository).save(argThat(d -> d.getStatus() == DocumentStatus.READY));
        verify(eventPublisher).publishEvent(any());
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

        IngestCompletedEvent event = new IngestCompletedEvent(id, "hash", "text", 1, false, true, null);

        assertThatThrownBy(() -> documentService.onIngestCompleted(event))
            .isInstanceOf(DuplicateDocumentException.class);

        verify(storageService).delete("key");
        verify(documentRepository).delete(doc);
    }

    @Test
    void trash_transitionsToTrash() {
        UUID id = UUID.randomUUID();
        Document doc = new Document();
        doc.setId(id);
        doc.setStatus(DocumentStatus.READY);

        when(documentRepository.findByIdAndOwnerId(id, "user1")).thenReturn(Optional.of(doc));
        when(documentRepository.save(any())).thenReturn(doc);

        documentService.trash(id, "user1");

        verify(documentRepository).save(argThat(d -> d.getStatus() == DocumentStatus.TRASH));
    }

    @Test
    void permanentDelete_deletesFromStorageAndPublishesEvent() {
        UUID id = UUID.randomUUID();
        Document doc = new Document();
        doc.setId(id);
        doc.setStatus(DocumentStatus.TRASH);
        doc.setStorageKey("key");

        when(documentRepository.findByIdAndOwnerId(id, "user1")).thenReturn(Optional.of(doc));
        when(documentRepository.save(any())).thenReturn(doc);

        documentService.permanentDelete(id, "user1");

        verify(storageService).delete("key");
        verify(eventPublisher).publishEvent(any());
    }
}
