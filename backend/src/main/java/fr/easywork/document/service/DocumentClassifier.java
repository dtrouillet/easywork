package fr.easywork.document.service;

import fr.easywork.document.domain.Correspondent;
import fr.easywork.document.domain.Document;
import fr.easywork.document.domain.DocumentType;
import fr.easywork.document.domain.Tag;
import fr.easywork.document.repository.CorrespondentRepository;
import fr.easywork.document.repository.DocumentTypeRepository;
import fr.easywork.document.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Heuristic auto-classification: matches the document's extracted text against
 * existing correspondent/tag/document-type names. Never mutates the document —
 * returns a {@link ClassificationSuggestionResult} for {@code SuggestionService}
 * to turn into a reviewable suggestion (ADR 0003). Only suggests fields that
 * are still unset on the document — never proposes replacing a manual
 * assignment that's already there.
 */
@Component
@RequiredArgsConstructor
class DocumentClassifier {

    private final CorrespondentRepository correspondentRepository;
    private final TagRepository tagRepository;
    private final DocumentTypeRepository documentTypeRepository;

    ClassificationSuggestionResult classify(Document doc) {
        String text = doc.getExtractedText();
        if (text == null || text.isBlank()) {
            return ClassificationSuggestionResult.empty();
        }
        String lowerText = text.toLowerCase(Locale.ROOT);

        Correspondent correspondent = classifyCorrespondent(doc, lowerText);
        Set<Tag> tags = classifyTags(doc, lowerText);
        DocumentType documentType = classifyDocumentType(doc, lowerText);

        return new ClassificationSuggestionResult(correspondent, documentType, null, tags);
    }

    private Correspondent classifyCorrespondent(Document doc, String lowerText) {
        if (doc.getCorrespondent() != null) {
            return null;
        }
        return findFirstMatch(correspondentRepository.findAll(), Correspondent::getName, lowerText).orElse(null);
    }

    private Set<Tag> classifyTags(Document doc, String lowerText) {
        if (!doc.getTags().isEmpty()) {
            return Set.of();
        }
        var matched = new HashSet<Tag>();
        for (Tag tag : tagRepository.findAll()) {
            if (matches(tag.getName(), lowerText)) {
                matched.add(tag);
            }
        }
        return matched;
    }

    private DocumentType classifyDocumentType(Document doc, String lowerText) {
        if (doc.getDocumentType() != null) {
            return null;
        }
        return findFirstMatch(documentTypeRepository.findAll(), DocumentType::getName, lowerText).orElse(null);
    }

    private static <T> Optional<T> findFirstMatch(
            Iterable<T> candidates, Function<T, String> nameExtractor, String lowerText) {
        for (T candidate : candidates) {
            if (matches(nameExtractor.apply(candidate), lowerText)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static boolean matches(String name, String lowerText) {
        return lowerText.contains(name.toLowerCase(Locale.ROOT));
    }
}
