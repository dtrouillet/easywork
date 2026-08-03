package fr.easywork.document.dto;

import java.util.Set;
import java.util.UUID;

/**
 * Payload for POST /api/v1/documents/{id}/suggestion/confirm. Explicit
 * per-field accept flags — deliberately not {@link DocumentUpdateRequest},
 * so confirming only ever applies fields the user accepted and never needs to
 * express "clear a field" (ADR 0003, sidesteps issue #22's PATCH null-means-
 * unset semantics for this flow). {@code acceptTagIds} selects which of the
 * suggestion's tags to apply; null/empty accepts none.
 */
public record ConfirmSuggestionRequest(
    boolean acceptCorrespondent,
    boolean acceptDocumentType,
    boolean acceptDocumentDate,
    Set<UUID> acceptTagIds
) {}
