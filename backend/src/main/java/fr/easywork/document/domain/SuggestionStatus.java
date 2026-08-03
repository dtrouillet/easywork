package fr.easywork.document.domain;

/** Lifecycle of a {@link DocumentClassificationSuggestion}. ADR 0003. */
public enum SuggestionStatus {
    PENDING,
    CONFIRMED,
    REJECTED
}
