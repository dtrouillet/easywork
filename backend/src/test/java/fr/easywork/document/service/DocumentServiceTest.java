package fr.easywork.document.service;

import fr.easywork.document.domain.Document;
import fr.easywork.document.domain.DocumentStatus;
import fr.easywork.document.dto.DocumentClassificationSuggestionDto;
import fr.easywork.document.dto.DocumentSearchCriteria;
import fr.easywork.document.dto.DocumentUpdateRequest;
import fr.easywork.document.event.DocumentDeletedEvent;
import fr.easywork.document.event.DocumentReadyEvent;
import fr.easywork.document.event.DocumentUploadedEvent;
import fr.easywork.document.event.IngestCompletedEvent;
import fr.easywork.document.exception.DocumentNotFoundException;
import fr.easywork.document.exception.EmptyFileException;
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
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
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
    @Mock UploadValidator uploadValidator;
    @Mock SuggestionService suggestionService;
    @Mock LearningService learningService;
    @Mock DocumentMapper mapper;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks DocumentService documentService;

    // --- upload ---

    @Test
    void upload_delegatesToValidatorAndStorage_thenSavesDocumentWithDetectedMimeType() {
        var file = new MockMultipartFile(
            "file", "invoice.pdf", "application/octet-stream", "content".getBytes(StandardCharsets.UTF_8));
        when(uploadValidator.validate(file)).thenReturn("application/pdf");
        when(storageService.store(any(), any(UUID.class))).thenReturn("key");
        // Simulates Hibernate assigning the generated id on the first persist, matching
        // DocumentService.upload()'s save-then-use-generated-id-then-save-again flow.
        when(documentRepository.save(any())).thenAnswer(inv -> {
            Document d = inv.getArgument(0);
            if (d.getId() == null) {
                d.setId(UUID.randomUUID());
            }
            return d;
        });

        documentService.upload(file, "user1");

        var docCaptor = org.mockito.ArgumentCaptor.forClass(Document.class);
        verify(documentRepository, times(2)).save(docCaptor.capture());
        Document savedDoc = docCaptor.getValue();
        assertThat(savedDoc.getMimeType()).isEqualTo("application/pdf");
        assertThat(savedDoc.getStorageKey()).isEqualTo("key");

        var eventCaptor = org.mockito.ArgumentCaptor.forClass(DocumentUploadedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().mimeType()).isEqualTo("application/pdf");
        assertThat(eventCaptor.getValue().storageKey()).isEqualTo("key");
    }

    @Test
    void upload_propagatesValidationException_andNeverTouchesStorage() {
        var file = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);
        when(uploadValidator.validate(file)).thenThrow(new EmptyFileException());

        assertThatThrownBy(() -> documentService.upload(file, "user1"))
            .isInstanceOf(EmptyFileException.class);

        verify(storageService, never()).store(any(), any());
        verify(documentRepository, never()).save(any());
    }

    // --- list ---

    @Test
    void list_delegatesToSpecification() {
        var doc = new Document();
        var page = new PageImpl<>(List.of(doc));
        when(documentRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(mapper.toDto(doc)).thenReturn(null);

        var criteria = new DocumentSearchCriteria(null, null, null, null, null, null);
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
        verify(learningService).recordConfirmation(doc);
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

    // --- reclassify ---

    @Test
    void reclassify_delegatesToSuggestionServiceAndReturnsItsResult() {
        UUID id = UUID.randomUUID();
        DocumentClassificationSuggestionDto dto =
            new DocumentClassificationSuggestionDto(id, null, null, null, List.of(), null, null, null, null, null);
        when(suggestionService.regenerateSuggestion(id, "user1")).thenReturn(dto);

        DocumentClassificationSuggestionDto result = documentService.reclassify(id, "user1");

        assertThat(result).isSameAs(dto);
        verify(suggestionService).regenerateSuggestion(id, "user1");
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

        documentService.onIngestCompleted(
            new IngestCompletedEvent(id, "hash", "text", 2, false, true, null, List.of()));

        verify(documentRepository).save(argThat(d -> d.getStatus() == DocumentStatus.READY));
        verify(eventPublisher).publishEvent(any(DocumentReadyEvent.class));
        verify(suggestionService).generateSuggestion(doc, List.of());
    }

    @Test
    void onIngestCompleted_deletesDuplicate_cleanlyWithoutThrowing() {
        // No caller can ever catch an exception thrown from inside this async
        // listener — it must clean up and return, not throw (regression test for
        // the stuck-RECEIVED-with-storage-already-deleted bug this used to cause).
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

        documentService.onIngestCompleted(
            new IngestCompletedEvent(id, "hash", "text", 1, false, true, null, List.of()));

        verify(storageService).delete("key");
        verify(documentRepository).delete(doc);
        verify(suggestionService, never()).generateSuggestion(any(), any());
        verify(eventPublisher, never()).publishEvent(any(DocumentReadyEvent.class));
    }

    @Test
    void onIngestCompleted_transitionsToFailed_andStoresError_onFailure() {
        UUID id = UUID.randomUUID();
        Document doc = new Document();
        doc.setId(id);
        doc.setOwnerId("user1");
        doc.setStatus(DocumentStatus.RECEIVED);

        when(documentRepository.findById(id)).thenReturn(Optional.of(doc));
        when(documentRepository.save(any())).thenReturn(doc);

        documentService.onIngestCompleted(
            new IngestCompletedEvent(id, null, null, null, false, false, "Tesseract crashed", List.of()));

        assertThat(doc.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(doc.getLastIngestError()).isEqualTo("Tesseract crashed");
        verify(suggestionService, never()).generateSuggestion(any(), any());
        verify(eventPublisher, never()).publishEvent(any(DocumentReadyEvent.class));
    }

    // --- existsDuplicate ---

    @Test
    void existsDuplicate_returnsTrue_whenAnotherDocumentSharesHash() {
        UUID otherId = UUID.randomUUID();
        Document other = new Document();
        other.setId(otherId);
        when(documentRepository.findByContentHashAndOwnerId("hash", "user1")).thenReturn(Optional.of(other));

        boolean result = documentService.existsDuplicate("hash", "user1", UUID.randomUUID());

        assertThat(result).isTrue();
    }

    @Test
    void existsDuplicate_returnsFalse_whenOnlyMatchIsSelf() {
        UUID id = UUID.randomUUID();
        Document self = new Document();
        self.setId(id);
        when(documentRepository.findByContentHashAndOwnerId("hash", "user1")).thenReturn(Optional.of(self));

        boolean result = documentService.existsDuplicate("hash", "user1", id);

        assertThat(result).isFalse();
    }

    @Test
    void existsDuplicate_returnsFalse_whenNoMatch() {
        when(documentRepository.findByContentHashAndOwnerId("hash", "user1")).thenReturn(Optional.empty());

        boolean result = documentService.existsDuplicate("hash", "user1", UUID.randomUUID());

        assertThat(result).isFalse();
    }

    // --- retryIngest ---

    @Test
    void retryIngest_transitionsFailedToReceived_andRepublishesUploadEvent() {
        UUID id = UUID.randomUUID();
        Document doc = new Document();
        doc.setId(id);
        doc.setOwnerId("user1");
        doc.setStatus(DocumentStatus.FAILED);
        doc.setLastIngestError("boom");
        doc.setStorageKey("key");
        doc.setMimeType("application/pdf");

        when(documentRepository.findByIdAndOwnerId(id, "user1")).thenReturn(Optional.of(doc));
        when(documentRepository.save(any())).thenReturn(doc);

        documentService.retryIngest(id, "user1");

        assertThat(doc.getStatus()).isEqualTo(DocumentStatus.RECEIVED);
        assertThat(doc.getLastIngestError()).isNull();

        var eventCaptor = org.mockito.ArgumentCaptor.forClass(DocumentUploadedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().documentId()).isEqualTo(id);
        assertThat(eventCaptor.getValue().storageKey()).isEqualTo("key");
    }

    @Test
    void retryIngest_throwsIllegalState_whenNotFailed() {
        UUID id = UUID.randomUUID();
        Document doc = readyDocument(id, "user1");
        when(documentRepository.findByIdAndOwnerId(id, "user1")).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> documentService.retryIngest(id, "user1"))
            .isInstanceOf(IllegalStateException.class);

        verify(documentRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
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
