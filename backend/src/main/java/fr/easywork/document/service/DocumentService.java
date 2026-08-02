package fr.easywork.document.service;

import fr.easywork.document.DocumentDuplicateCheck;
import fr.easywork.document.domain.Document;
import fr.easywork.document.domain.DocumentStatus;
import fr.easywork.document.dto.DocumentDto;
import fr.easywork.document.dto.DocumentSearchCriteria;
import fr.easywork.document.dto.DocumentUpdateRequest;
import fr.easywork.document.dto.PageResponse;
import fr.easywork.document.event.DocumentDeletedEvent;
import fr.easywork.document.event.DocumentReadyEvent;
import fr.easywork.document.event.DocumentUploadedEvent;
import fr.easywork.document.event.IngestCompletedEvent;
import fr.easywork.document.exception.DocumentNotFoundException;
import fr.easywork.document.mapper.DocumentMapper;
import fr.easywork.document.repository.CorrespondentRepository;
import fr.easywork.document.repository.DocumentRepository;
import fr.easywork.document.repository.DocumentSpecifications;
import fr.easywork.document.repository.DocumentTypeRepository;
import fr.easywork.document.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.Instant;
import java.util.HashSet;
import java.util.UUID;

// This is the central document lifecycle orchestrator (upload, classify, archive,
// trash, GDPR erasure); its collaborators are the module's own repositories/services,
// not an accidental coupling smell — splitting it up is a larger refactor, not a
// side effect of adding one more classification collaborator.
@SuppressWarnings("PMD.CouplingBetweenObjects")
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DocumentService implements DocumentDuplicateCheck {

    private final DocumentRepository documentRepository;
    private final TagRepository tagRepository;
    private final CorrespondentRepository correspondentRepository;
    private final DocumentTypeRepository documentTypeRepository;
    private final StorageService storageService;
    private final UploadValidator uploadValidator;
    private final DocumentClassifier documentClassifier;
    private final DocumentMapper mapper;
    private final ApplicationEventPublisher eventPublisher;

    public PageResponse<DocumentDto> list(String ownerId, DocumentSearchCriteria criteria, Pageable pageable) {
        Specification<Document> spec = DocumentSpecifications.from(ownerId, criteria);
        Page<Document> page = documentRepository.findAll(spec, pageable);
        return new PageResponse<>(
            page.getContent().stream().map(mapper::toDto).toList(),
            new PageResponse.PageMetadata(
                page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages())
        );
    }

    @PreAuthorize("isAuthenticated()")
    public DocumentDto get(UUID id, String ownerId) {
        return documentRepository.findByIdAndOwnerId(id, ownerId)
            .map(mapper::toDto)
            .orElseThrow(() -> new DocumentNotFoundException(id));
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public DocumentDto upload(MultipartFile file, String ownerId) {
        String detectedMimeType = uploadValidator.validate(file);

        Document doc = new Document();
        doc.setTitle(file.getOriginalFilename());
        doc.setOriginalFilename(file.getOriginalFilename());
        doc.setMimeType(detectedMimeType);
        doc.setFileSize(file.getSize());
        doc.setOwnerId(ownerId);
        doc.setStatus(DocumentStatus.RECEIVED);
        // Persist first so Hibernate assigns the generated id — the storage key needs
        // that id, and it must exist as a real row before storageService.store() is
        // referenced from it (a manually pre-assigned id on a @GeneratedValue entity
        // makes Hibernate reject the subsequent save as a detached entity).
        documentRepository.save(doc);

        UUID docId = doc.getId();
        String storageKey = storageService.store(file, docId);
        doc.setStorageKey(storageKey);
        documentRepository.save(doc);

        eventPublisher.publishEvent(
            new DocumentUploadedEvent(docId, storageKey, detectedMimeType, ownerId));
        return mapper.toDto(doc);
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public DocumentDto update(UUID id, String ownerId, DocumentUpdateRequest request) {
        Document doc = getOwned(id, ownerId);

        if (request.title() != null) {
            doc.setTitle(request.title());
        }
        if (request.documentDate() != null) {
            doc.setDocumentDate(request.documentDate());
        }
        if (request.correspondentId() != null) {
            doc.setCorrespondent(correspondentRepository.findById(request.correspondentId())
                .orElseThrow(() -> new IllegalArgumentException(
                    "Correspondent not found: " + request.correspondentId())));
        }
        if (request.documentTypeId() != null) {
            doc.setDocumentType(documentTypeRepository.findById(request.documentTypeId())
                .orElseThrow(() -> new IllegalArgumentException(
                    "DocumentType not found: " + request.documentTypeId())));
        }
        if (request.tagIds() != null) {
            doc.setTags(new HashSet<>(tagRepository.findAllById(request.tagIds())));
        }

        documentRepository.save(doc);

        // Re-index if the document is in a searchable state
        if (doc.getStatus() == DocumentStatus.READY || doc.getStatus() == DocumentStatus.ARCHIVED) {
            eventPublisher.publishEvent(new DocumentReadyEvent(
                doc.getId(), doc.getTitle(), doc.getExtractedText(), doc.getMimeType(),
                doc.getDocumentDate(),
                doc.getTags().stream().map(t -> t.getName()).toList(),
                doc.getCorrespondent() != null ? doc.getCorrespondent().getName() : null,
                doc.getDocumentType() != null ? doc.getDocumentType().getName() : null,
                doc.getOwnerId()
            ));
        }

        return mapper.toDto(doc);
    }

    @PreAuthorize("isAuthenticated()")
    public InputStream download(UUID id, String ownerId) {
        Document doc = getOwned(id, ownerId);
        if (doc.getStorageKey() == null) {
            throw new DocumentNotFoundException(id);
        }
        return storageService.download(doc.getStorageKey());
    }

    @Override
    public boolean existsDuplicate(String contentHash, String ownerId, UUID excludingDocumentId) {
        return documentRepository.findByContentHashAndOwnerId(contentHash, ownerId)
            .filter(existing -> !existing.getId().equals(excludingDocumentId))
            .isPresent();
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    @ApplicationModuleListener
    public void onIngestCompleted(IngestCompletedEvent event) {
        Document doc = documentRepository.findById(event.documentId())
            .orElseThrow(() -> new DocumentNotFoundException(event.documentId()));

        if (!event.success()) {
            doc.transitionTo(DocumentStatus.FAILED);
            doc.setLastIngestError(event.errorMessage());
            documentRepository.save(doc);
            return;
        }

        // Duplicate detection. This runs inside an async event listener — there's no
        // HTTP caller left to hand a 409 to, so the duplicate is deleted outright
        // instead of throwing (an exception here has nowhere to go but the log, and
        // leaves this listener's event_publication permanently incomplete).
        boolean isDuplicate = documentRepository.findByContentHashAndOwnerId(event.contentHash(), doc.getOwnerId())
            .filter(existing -> !existing.getId().equals(doc.getId()))
            .isPresent();
        if (isDuplicate) {
            storageService.delete(doc.getStorageKey());
            documentRepository.delete(doc);
            return;
        }

        doc.setContentHash(event.contentHash());
        doc.setExtractedText(event.extractedText());
        doc.setPageCount(event.pageCount());
        doc.setOcrApplied(event.ocrApplied());
        // Walk through the lifecycle: RECEIVED → EXTRACTING → (OCR →) CLASSIFYING → READY
        doc.transitionTo(DocumentStatus.EXTRACTING);
        if (event.ocrApplied()) {
            doc.transitionTo(DocumentStatus.OCR);
        }
        doc.transitionTo(DocumentStatus.CLASSIFYING);
        documentClassifier.classify(doc);
        doc.transitionTo(DocumentStatus.READY);

        documentRepository.save(doc);
        eventPublisher.publishEvent(new DocumentReadyEvent(
            doc.getId(), doc.getTitle(), doc.getExtractedText(), doc.getMimeType(),
            doc.getDocumentDate(),
            doc.getTags().stream().map(t -> t.getName()).toList(),
            doc.getCorrespondent() != null ? doc.getCorrespondent().getName() : null,
            doc.getDocumentType() != null ? doc.getDocumentType().getName() : null,
            doc.getOwnerId()
        ));
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public void trash(UUID id, String ownerId) {
        Document doc = getOwned(id, ownerId);
        doc.transitionTo(DocumentStatus.TRASH);
        doc.setTrashedAt(Instant.now());
        documentRepository.save(doc);
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public void archive(UUID id, String ownerId) {
        Document doc = getOwned(id, ownerId);
        doc.transitionTo(DocumentStatus.ARCHIVED);
        doc.setArchivedAt(Instant.now());
        documentRepository.save(doc);
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public void restore(UUID id, String ownerId) {
        Document doc = getOwned(id, ownerId);
        doc.transitionTo(DocumentStatus.READY);
        documentRepository.save(doc);
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public void retryIngest(UUID id, String ownerId) {
        Document doc = getOwned(id, ownerId);
        doc.transitionTo(DocumentStatus.RECEIVED);
        doc.setLastIngestError(null);
        documentRepository.save(doc);

        eventPublisher.publishEvent(new DocumentUploadedEvent(
            doc.getId(), doc.getStorageKey(), doc.getMimeType(), doc.getOwnerId()));
    }

    /**
     * ADR 0001: real GDPR erasure — scrubs personal data, deletes the DB row,
     * deletes from MinIO and triggers search index removal via event.
     * The Envers audit entry (status transitions only) survives without personal data.
     */
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public void permanentDelete(UUID id, String ownerId) {
        Document doc = getOwned(id, ownerId);
        doc.transitionTo(DocumentStatus.DELETED);
        doc.setDeletedAt(Instant.now());

        String storageKey = doc.getStorageKey();

        // Scrub personal data fields (writes a final audit revision with null personal data)
        doc.scrubPersonalData();
        documentRepository.save(doc);

        // Real deletion from PostgreSQL — audit entry survives (only non-personal fields)
        documentRepository.delete(doc);

        // MinIO and search index removal via events (decoupled, async-safe)
        if (storageKey != null) {
            storageService.delete(storageKey);
        }
        eventPublisher.publishEvent(new DocumentDeletedEvent(id));
    }

    private Document getOwned(UUID id, String ownerId) {
        return documentRepository.findByIdAndOwnerId(id, ownerId)
            .orElseThrow(() -> new DocumentNotFoundException(id));
    }
}
