package fr.easywork.document.service;

import fr.easywork.document.domain.Correspondent;
import fr.easywork.document.domain.Document;
import fr.easywork.document.domain.DocumentStatus;
import fr.easywork.document.domain.Tag;
import fr.easywork.document.dto.CorrespondentDto;
import fr.easywork.document.event.DocumentReadyEvent;
import fr.easywork.document.exception.DuplicateNameException;
import fr.easywork.document.exception.EntityInUseException;
import fr.easywork.document.mapper.DocumentMapper;
import fr.easywork.document.repository.CorrespondentRepository;
import fr.easywork.document.repository.DocumentRepository;
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
public class CorrespondentService {

    private final CorrespondentRepository correspondentRepository;
    private final DocumentRepository documentRepository;
    private final DocumentMapper mapper;
    private final ApplicationEventPublisher eventPublisher;

    public List<CorrespondentDto> list() {
        return correspondentRepository.findAll().stream().map(mapper::toDto).toList();
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public CorrespondentDto create(String name) {
        if (correspondentRepository.findByName(name).isPresent()) {
            throw new DuplicateNameException("Correspondent", name);
        }
        return mapper.toDto(correspondentRepository.save(new Correspondent(name)));
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public CorrespondentDto update(UUID id, String name) {
        Correspondent correspondent = correspondentRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Correspondent not found: " + id));
        correspondentRepository.findByName(name)
            .filter(existing -> !existing.getId().equals(id))
            .ifPresent(existing -> { throw new DuplicateNameException("Correspondent", name); });

        correspondent.setName(name);
        return mapper.toDto(correspondentRepository.save(correspondent));
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public void delete(UUID id) {
        if (documentRepository.existsByCorrespondentId(id)) {
            throw new EntityInUseException("Correspondent", id);
        }
        correspondentRepository.deleteById(id);
    }

    /**
     * Reassigns every document with {@code sourceId} as correspondent to
     * {@code targetId}, then deletes the source. Re-indexes affected
     * READY/ARCHIVED documents since their correspondent actually changes.
     */
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public void merge(UUID sourceId, UUID targetId) {
        if (sourceId.equals(targetId)) {
            throw new IllegalArgumentException("Cannot merge a correspondent with itself");
        }
        Correspondent source = correspondentRepository.findById(sourceId)
            .orElseThrow(() -> new IllegalArgumentException("Correspondent not found: " + sourceId));
        Correspondent target = correspondentRepository.findById(targetId)
            .orElseThrow(() -> new IllegalArgumentException("Correspondent not found: " + targetId));

        List<Document> affected = documentRepository.findAllByCorrespondentId(sourceId);
        affected.forEach(doc -> doc.setCorrespondent(target));
        documentRepository.saveAll(affected);

        affected.stream()
            .filter(doc -> doc.getStatus() == DocumentStatus.READY || doc.getStatus() == DocumentStatus.ARCHIVED)
            .forEach(this::publishReindex);

        correspondentRepository.delete(source);
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
