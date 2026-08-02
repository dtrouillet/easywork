package fr.easywork.document.service;

import fr.easywork.document.domain.Document;
import fr.easywork.document.domain.DocumentStatus;
import fr.easywork.document.domain.Tag;
import fr.easywork.document.dto.TagDto;
import fr.easywork.document.event.DocumentReadyEvent;
import fr.easywork.document.exception.DuplicateNameException;
import fr.easywork.document.exception.EntityInUseException;
import fr.easywork.document.mapper.DocumentMapper;
import fr.easywork.document.repository.DocumentRepository;
import fr.easywork.document.repository.TagRepository;
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
public class TagService {

    private final TagRepository tagRepository;
    private final DocumentRepository documentRepository;
    private final DocumentMapper mapper;
    private final ApplicationEventPublisher eventPublisher;

    public List<TagDto> list() {
        return tagRepository.findAll().stream().map(mapper::toDto).toList();
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public TagDto create(String name, String color) {
        if (tagRepository.findByName(name).isPresent()) {
            throw new DuplicateNameException("Tag", name);
        }
        Tag tag = new Tag(name);
        tag.setColor(color);
        return mapper.toDto(tagRepository.save(tag));
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public TagDto update(UUID id, String name, String color) {
        Tag tag = tagRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Tag not found: " + id));
        tagRepository.findByName(name)
            .filter(existing -> !existing.getId().equals(id))
            .ifPresent(existing -> { throw new DuplicateNameException("Tag", name); });

        tag.setName(name);
        tag.setColor(color);
        return mapper.toDto(tagRepository.save(tag));
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public void delete(UUID id) {
        if (documentRepository.existsByTagsId(id)) {
            throw new EntityInUseException("Tag", id);
        }
        tagRepository.deleteById(id);
    }

    /**
     * Reassigns every document tagged with {@code sourceId} to also carry
     * {@code targetId}, then deletes the source tag. Re-indexes affected
     * READY/ARCHIVED documents since their tag set actually changes (unlike a
     * plain rename, which doesn't touch any document).
     */
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public void merge(UUID sourceId, UUID targetId) {
        if (sourceId.equals(targetId)) {
            throw new IllegalArgumentException("Cannot merge a tag with itself");
        }
        Tag source = tagRepository.findById(sourceId)
            .orElseThrow(() -> new IllegalArgumentException("Tag not found: " + sourceId));
        Tag target = tagRepository.findById(targetId)
            .orElseThrow(() -> new IllegalArgumentException("Tag not found: " + targetId));

        List<Document> affected = documentRepository.findAllByTagsId(sourceId);
        for (Document doc : affected) {
            doc.getTags().remove(source);
            doc.getTags().add(target);
        }
        documentRepository.saveAll(affected);

        affected.stream()
            .filter(doc -> doc.getStatus() == DocumentStatus.READY || doc.getStatus() == DocumentStatus.ARCHIVED)
            .forEach(this::publishReindex);

        tagRepository.delete(source);
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
