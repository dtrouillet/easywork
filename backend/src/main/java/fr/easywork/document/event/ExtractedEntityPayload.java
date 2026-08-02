package fr.easywork.document.event;

/**
 * One value found by the ingest module's deterministic {@code EntityExtractor}
 * (date, amount, IBAN, reference number), carried across the {@code ingest} →
 * {@code document} module boundary as part of {@link IngestCompletedEvent}
 * (ADR 0003) — deliberately not the JPA entity itself, so the event contract
 * doesn't leak persistence details.
 */
public record ExtractedEntityPayload(ExtractedEntityType type, String rawValue, String normalizedValue) {}
