package fr.easywork.document.service;

import fr.easywork.document.domain.Document;
import fr.easywork.document.domain.DocumentStatus;
import fr.easywork.document.domain.DocumentType;
import fr.easywork.document.domain.Tag;
import fr.easywork.document.dto.DocumentTypeDto;
import fr.easywork.document.event.DocumentReadyEvent;
import fr.easywork.document.exception.DuplicateNameException;
import fr.easywork.document.exception.EntityInUseException;
import fr.easywork.document.mapper.DocumentMapper;
import fr.easywork.document.repository.DocumentRepository;
import fr.easywork.document.repository.DocumentTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DocumentTypeService {

    private final DocumentTypeRepository documentTypeRepository;
    private final DocumentRepository documentRepository;
    private final DocumentMapper mapper;
    private final ApplicationEventPublisher eventPublisher;

    public List<DocumentTypeDto> list() {
        return documentTypeRepository.findAll().stream().map(mapper::toDto).toList();
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public DocumentTypeDto create(String name, Integer retentionDays) {
        if (documentTypeRepository.findByName(name).isPresent()) {
            throw new DuplicateNameException("DocumentType", name);
        }
        DocumentType type = new DocumentType(name);
        type.setRetentionDays(retentionDays);
        return mapper.toDto(documentTypeRepository.save(type));
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public DocumentTypeDto update(UUID id, String name, Integer retentionDays) {
        DocumentType type = documentTypeRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("DocumentType not found: " + id));
        documentTypeRepository.findByName(name)
            .filter(existing -> !existing.getId().equals(id))
            .ifPresent(existing -> { throw new DuplicateNameException("DocumentType", name); });

        type.setName(name);
        type.setRetentionDays(retentionDays);
        return mapper.toDto(documentTypeRepository.save(type));
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public void delete(UUID id) {
        if (documentRepository.existsByDocumentTypeId(id)) {
            throw new EntityInUseException("DocumentType", id);
        }
        documentTypeRepository.deleteById(id);
    }

    /**
     * Reassigns every document with {@code sourceId} as document type to
     * {@code targetId}, then deletes the source. Re-indexes affected
     * READY/ARCHIVED documents since their type actually changes.
     */
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public void merge(UUID sourceId, UUID targetId) {
        if (sourceId.equals(targetId)) {
            throw new IllegalArgumentException("Cannot merge a document type with itself");
        }
        DocumentType source = documentTypeRepository.findById(sourceId)
            .orElseThrow(() -> new IllegalArgumentException("DocumentType not found: " + sourceId));
        DocumentType target = documentTypeRepository.findById(targetId)
            .orElseThrow(() -> new IllegalArgumentException("DocumentType not found: " + targetId));

        List<Document> affected = documentRepository.findAllByDocumentTypeId(sourceId);
        affected.forEach(doc -> doc.setDocumentType(target));
        documentRepository.saveAll(affected);

        affected.stream()
            .filter(doc -> doc.getStatus() == DocumentStatus.READY || doc.getStatus() == DocumentStatus.ARCHIVED)
            .forEach(this::publishReindex);

        documentTypeRepository.delete(source);
    }

    private void publishReindex(Document doc) {
        eventPublisher.publishEvent(new DocumentReadyEvent(
            doc.getId(), doc.getTitle(), doc.getExtractedText(), doc.getMimeType(),
            doc.getDocumentDate(),
            doc.getTags().stream().map(Tag::getName).toList(),
            doc.getCorrespondent() != null ? doc.getCorrespondent().getName() : null,
            doc.getDocumentType() != null ? doc.getDocumentType().getName() : null,
            doc.getOwnerId()
        ));
    }
}
