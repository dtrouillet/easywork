package fr.easywork.document.service;

import fr.easywork.document.domain.Correspondent;
import fr.easywork.document.domain.Document;
import fr.easywork.document.domain.DocumentClassificationSuggestion;
import fr.easywork.document.domain.DocumentExtractedEntity;
import fr.easywork.document.domain.DocumentStatus;
import fr.easywork.document.domain.DocumentType;
import fr.easywork.document.domain.SuggestionSource;
import fr.easywork.document.domain.Tag;
import fr.easywork.document.dto.ConfirmSuggestionRequest;
import fr.easywork.document.dto.DocumentClassificationSuggestionDto;
import fr.easywork.document.event.DocumentReadyEvent;
import fr.easywork.document.event.ExtractedEntityPayload;
import fr.easywork.document.event.ExtractedEntityType;
import fr.easywork.document.exception.DocumentNotFoundException;
import fr.easywork.document.exception.SuggestionNotFoundException;
import fr.easywork.document.mapper.DocumentMapper;
import fr.easywork.document.repository.DocumentClassificationSuggestionRepository;
import fr.easywork.document.repository.DocumentExtractedEntityRepository;
import fr.easywork.document.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Turns heuristic + learned classification signals into a reviewable
 * {@link DocumentClassificationSuggestion} and applies a user's confirm/reject
 * decision (ADR 0003). Never mutates {@link Document} on generation — only
 * {@link #confirmSuggestion} writes accepted fields back to the document.
 */
// Orchestrates the classifier, learned-association resolver, and three repositories that
// make up the suggestion workflow (ADR 0003); its collaborators are this module's own
// domain services/repositories, not an accidental coupling smell.
@SuppressWarnings("PMD.CouplingBetweenObjects")
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SuggestionService {

    private final DocumentClassifier documentClassifier;
    private final LearnedAssociationResolver learnedAssociationResolver;
    private final LearningService learningService;
    private final DocumentRepository documentRepository;
    private final DocumentExtractedEntityRepository extractedEntityRepository;
    private final DocumentClassificationSuggestionRepository suggestionRepository;
    private final DocumentMapper mapper;
    private final ApplicationEventPublisher eventPublisher;

    /** Called from the ingest completion flow: persists newly extracted entities, then generates a suggestion. */
    @Transactional
    public void generateSuggestion(Document doc, List<ExtractedEntityPayload> newExtractedEntities) {
        persistExtractedEntities(doc, newExtractedEntities);
        upsertSuggestion(doc);
    }

    /**
     * Re-runs classification on demand (the "Re-run auto-classification" action),
     * reusing entities already extracted.
     */
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public DocumentClassificationSuggestionDto regenerateSuggestion(UUID documentId, String ownerId) {
        Document doc = getOwnedDocument(documentId, ownerId);
        return mapper.toDto(upsertSuggestion(doc));
    }

    @PreAuthorize("isAuthenticated()")
    public DocumentClassificationSuggestionDto getSuggestion(UUID documentId, String ownerId) {
        getOwnedDocument(documentId, ownerId);
        return suggestionRepository.findById(documentId)
            .map(mapper::toDto)
            .orElseThrow(() -> new SuggestionNotFoundException(documentId));
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public DocumentClassificationSuggestionDto confirmSuggestion(
            UUID documentId, String ownerId, ConfirmSuggestionRequest request) {
        Document doc = getOwnedDocument(documentId, ownerId);
        DocumentClassificationSuggestion suggestion = getOwnedSuggestion(documentId);

        if (request.acceptCorrespondent() && suggestion.getSuggestedCorrespondent() != null) {
            doc.setCorrespondent(suggestion.getSuggestedCorrespondent());
        }
        if (request.acceptDocumentType() && suggestion.getSuggestedDocumentType() != null) {
            doc.setDocumentType(suggestion.getSuggestedDocumentType());
        }
        if (request.acceptDocumentDate() && suggestion.getSuggestedDocumentDate() != null) {
            doc.setDocumentDate(suggestion.getSuggestedDocumentDate());
        }
        Set<UUID> acceptedTagIds = request.acceptTagIds() != null ? request.acceptTagIds() : Set.of();
        if (!acceptedTagIds.isEmpty()) {
            Set<Tag> tags = new HashSet<>(doc.getTags());
            suggestion.getSuggestedTags().stream()
                .filter(tag -> acceptedTagIds.contains(tag.getId()))
                .forEach(tags::add);
            doc.setTags(tags);
        }
        documentRepository.save(doc);
        // Confirming is as strong a "the user vouches for this" signal as a manual PATCH correction.
        learningService.recordConfirmation(doc);

        suggestion.confirm();
        suggestionRepository.save(suggestion);

        publishReindexEventIfSearchable(doc);

        return mapper.toDto(suggestion);
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public DocumentClassificationSuggestionDto rejectSuggestion(UUID documentId, String ownerId) {
        getOwnedDocument(documentId, ownerId);
        DocumentClassificationSuggestion suggestion = getOwnedSuggestion(documentId);
        suggestion.reject();
        suggestionRepository.save(suggestion);
        return mapper.toDto(suggestion);
    }

    private DocumentClassificationSuggestion upsertSuggestion(Document doc) {
        ClassificationSuggestionResult heuristic = documentClassifier.classify(doc);

        Correspondent effectiveCorrespondent =
            heuristic.correspondent() != null ? heuristic.correspondent() : doc.getCorrespondent();
        DocumentType effectiveType =
            heuristic.documentType() != null ? heuristic.documentType() : doc.getDocumentType();

        Set<Tag> learnedTags = learnedAssociationResolver.resolve(effectiveCorrespondent, effectiveType);
        Set<Tag> additionalFromLearning = new HashSet<>(learnedTags);
        additionalFromLearning.removeAll(heuristic.tags());

        Set<Tag> suggestedTags = new HashSet<>(heuristic.tags());
        suggestedTags.addAll(learnedTags);
        suggestedTags.removeAll(doc.getTags());

        LocalDate suggestedDate = doc.getDocumentDate() == null ? bestExtractedDate(doc.getId()) : null;

        SuggestionSource source =
            additionalFromLearning.isEmpty() ? SuggestionSource.HEURISTIC : SuggestionSource.LEARNED;

        DocumentClassificationSuggestion suggestion = suggestionRepository.findById(doc.getId()).orElse(null);
        if (suggestion == null) {
            suggestion = new DocumentClassificationSuggestion(doc.getId(), source);
        } else {
            suggestion.regenerate(source);
        }
        suggestion.setSuggestedCorrespondent(heuristic.correspondent());
        suggestion.setSuggestedDocumentType(heuristic.documentType());
        suggestion.setSuggestedDocumentDate(suggestedDate);
        suggestion.setSuggestedTags(suggestedTags);

        return suggestionRepository.save(suggestion);
    }

    private LocalDate bestExtractedDate(UUID documentId) {
        return extractedEntityRepository.findByDocumentId(documentId).stream()
            .filter(entity -> entity.getEntityType() == ExtractedEntityType.DATE)
            .map(DocumentExtractedEntity::getNormalizedValue)
            .filter(Objects::nonNull)
            .map(SuggestionService::parseDateOrNull)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    }

    private static LocalDate parseDateOrNull(String isoDate) {
        try {
            return LocalDate.parse(isoDate);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private void persistExtractedEntities(Document doc, List<ExtractedEntityPayload> payloads) {
        if (payloads == null || payloads.isEmpty()) {
            return;
        }
        List<DocumentExtractedEntity> entities = payloads.stream()
            .map(payload -> new DocumentExtractedEntity(
                doc, payload.type(), payload.rawValue(), payload.normalizedValue()))
            .toList();
        extractedEntityRepository.saveAll(entities);
    }

    private Document getOwnedDocument(UUID id, String ownerId) {
        return documentRepository.findByIdAndOwnerId(id, ownerId)
            .orElseThrow(() -> new DocumentNotFoundException(id));
    }

    private DocumentClassificationSuggestion getOwnedSuggestion(UUID documentId) {
        return suggestionRepository.findById(documentId)
            .orElseThrow(() -> new SuggestionNotFoundException(documentId));
    }

    private void publishReindexEventIfSearchable(Document doc) {
        if (doc.getStatus() == DocumentStatus.READY || doc.getStatus() == DocumentStatus.ARCHIVED) {
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
}
