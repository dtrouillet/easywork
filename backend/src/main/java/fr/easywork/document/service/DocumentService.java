package fr.easywork.document.service;

import fr.easywork.document.domain.Document;
import fr.easywork.document.domain.DocumentStatus;
import fr.easywork.document.dto.DocumentDto;
import fr.easywork.document.dto.PageResponse;
import fr.easywork.document.event.DocumentDeletedEvent;
import fr.easywork.document.event.DocumentReadyEvent;
import fr.easywork.document.event.DocumentUploadedEvent;
import fr.easywork.document.event.IngestCompletedEvent;
import fr.easywork.document.exception.DocumentNotFoundException;
import fr.easywork.document.exception.DuplicateDocumentException;
import fr.easywork.document.mapper.DocumentMapper;
import fr.easywork.document.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final StorageService storageService;
    private final DocumentMapper mapper;
    private final ApplicationEventPublisher eventPublisher;

    public PageResponse<DocumentDto> list(String ownerId, Pageable pageable) {
        Page<Document> page = documentRepository
            .findByOwnerIdAndStatusNot(ownerId, DocumentStatus.DELETED, pageable);
        return new PageResponse<>(
            page.getContent().stream().map(mapper::toDto).toList(),
            new PageResponse.PageMetadata(
                page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages())
        );
    }

    public DocumentDto get(UUID id, String ownerId) {
        return documentRepository.findByIdAndOwnerId(id, ownerId)
            .map(mapper::toDto)
            .orElseThrow(() -> new DocumentNotFoundException(id));
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public DocumentDto upload(MultipartFile file, String ownerId) {
        UUID docId = UUID.randomUUID();
        String storageKey = storageService.store(file, docId);

        Document doc = new Document();
        doc.setId(docId);
        doc.setTitle(file.getOriginalFilename());
        doc.setOriginalFilename(file.getOriginalFilename());
        doc.setMimeType(file.getContentType());
        doc.setFileSize(file.getSize());
        doc.setStorageKey(storageKey);
        doc.setOwnerId(ownerId);
        doc.setStatus(DocumentStatus.RECEIVED);

        documentRepository.save(doc);
        eventPublisher.publishEvent(
            new DocumentUploadedEvent(docId, storageKey, file.getContentType(), ownerId));
        return mapper.toDto(doc);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    @ApplicationModuleListener
    public void onIngestCompleted(IngestCompletedEvent event) {
        Document doc = documentRepository.findById(event.documentId())
            .orElseThrow(() -> new DocumentNotFoundException(event.documentId()));

        if (!event.success()) {
            return;
        }

        // Duplicate detection
        documentRepository.findByContentHashAndOwnerId(event.contentHash(), doc.getOwnerId())
            .filter(existing -> !existing.getId().equals(doc.getId()))
            .ifPresent(existing -> {
                storageService.delete(doc.getStorageKey());
                documentRepository.delete(doc);
                throw new DuplicateDocumentException(existing.getId());
            });

        doc.setContentHash(event.contentHash());
        doc.setExtractedText(event.extractedText());
        doc.setPageCount(event.pageCount());
        doc.setOcrApplied(event.ocrApplied());
        doc.transitionTo(DocumentStatus.CLASSIFYING);
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
    public void trash(UUID id, String ownerId) {
        Document doc = getOwned(id, ownerId);
        doc.transitionTo(DocumentStatus.TRASH);
        doc.setTrashedAt(Instant.now());
        documentRepository.save(doc);
    }

    @Transactional
    public void archive(UUID id, String ownerId) {
        Document doc = getOwned(id, ownerId);
        doc.transitionTo(DocumentStatus.ARCHIVED);
        doc.setArchivedAt(Instant.now());
        documentRepository.save(doc);
    }

    @Transactional
    public void restore(UUID id, String ownerId) {
        Document doc = getOwned(id, ownerId);
        doc.transitionTo(DocumentStatus.READY);
        documentRepository.save(doc);
    }

    @Transactional
    public void permanentDelete(UUID id, String ownerId) {
        Document doc = getOwned(id, ownerId);
        doc.transitionTo(DocumentStatus.DELETED);
        doc.setDeletedAt(Instant.now());
        storageService.delete(doc.getStorageKey());
        documentRepository.save(doc);
        eventPublisher.publishEvent(new DocumentDeletedEvent(id));
    }

    private Document getOwned(UUID id, String ownerId) {
        return documentRepository.findByIdAndOwnerId(id, ownerId)
            .orElseThrow(() -> new DocumentNotFoundException(id));
    }
}
