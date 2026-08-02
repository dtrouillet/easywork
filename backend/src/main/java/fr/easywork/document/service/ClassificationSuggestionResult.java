package fr.easywork.document.service;

import fr.easywork.document.domain.Correspondent;
import fr.easywork.document.domain.DocumentType;
import fr.easywork.document.domain.Tag;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * Candidate classification for one document, produced by heuristic
 * substring-matching ({@code DocumentClassifier}) and/or learned associations
 * ({@code LearnedAssociationResolver}) — never applied directly to a
 * {@link fr.easywork.document.domain.Document}; {@code SuggestionService}
 * turns this into a {@link fr.easywork.document.domain.DocumentClassificationSuggestion}
 * for the user to confirm or reject (ADR 0003).
 */
record ClassificationSuggestionResult(
    Correspondent correspondent,
    DocumentType documentType,
    LocalDate documentDate,
    Set<Tag> tags
) {

    static ClassificationSuggestionResult empty() {
        return new ClassificationSuggestionResult(null, null, null, Set.of());
    }

    boolean isEmpty() {
        return correspondent == null && documentType == null && documentDate == null && tags.isEmpty();
    }

    /** Merges another result's tags in and fills any still-unset correspondent/type/date. */
    ClassificationSuggestionResult mergedWith(ClassificationSuggestionResult other) {
        Set<Tag> merged = new HashSet<>(tags);
        merged.addAll(other.tags);
        return new ClassificationSuggestionResult(
            correspondent != null ? correspondent : other.correspondent,
            documentType != null ? documentType : other.documentType,
            documentDate != null ? documentDate : other.documentDate,
            merged);
    }

    ClassificationSuggestionResult withDocumentDate(LocalDate date) {
        return new ClassificationSuggestionResult(correspondent, documentType, date, tags);
    }
}
