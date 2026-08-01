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
import java.util.function.Function;

/**
 * Heuristic auto-classification: matches the document's extracted text against
 * existing correspondent/tag/document-type names. Only fills fields that are
 * still unset — never overwrites a manual assignment.
 */
@Component
@RequiredArgsConstructor
class DocumentClassifier {

    private final CorrespondentRepository correspondentRepository;
    private final TagRepository tagRepository;
    private final DocumentTypeRepository documentTypeRepository;

    void classify(Document doc) {
        String text = doc.getExtractedText();
        if (text == null || text.isBlank()) {
            return;
        }
        String lowerText = text.toLowerCase(Locale.ROOT);

        classifyCorrespondent(doc, lowerText);
        classifyTags(doc, lowerText);
        classifyDocumentType(doc, lowerText);
    }

    private void classifyCorrespondent(Document doc, String lowerText) {
        if (doc.getCorrespondent() != null) {
            return;
        }
        findFirstMatch(correspondentRepository.findAll(), Correspondent::getName, lowerText)
            .ifPresent(doc::setCorrespondent);
    }

    private void classifyTags(Document doc, String lowerText) {
        if (!doc.getTags().isEmpty()) {
            return;
        }
        var matched = new HashSet<Tag>();
        for (Tag tag : tagRepository.findAll()) {
            if (matches(tag.getName(), lowerText)) {
                matched.add(tag);
            }
        }
        doc.setTags(matched);
    }

    private void classifyDocumentType(Document doc, String lowerText) {
        if (doc.getDocumentType() != null) {
            return;
        }
        findFirstMatch(documentTypeRepository.findAll(), DocumentType::getName, lowerText)
            .ifPresent(doc::setDocumentType);
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
