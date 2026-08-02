package fr.easywork.document.domain;

/**
 * How a {@link DocumentClassificationSuggestion} was produced. ADR 0003 covers
 * Phase 1 only (HEURISTIC, LEARNED) — an LLM-assisted source is deferred to the
 * future Phase 2 ADR 0004, not added here ahead of need.
 */
public enum SuggestionSource {
    HEURISTIC,
    LEARNED
}
