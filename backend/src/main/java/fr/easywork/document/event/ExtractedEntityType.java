package fr.easywork.document.event;

/**
 * Kind of value a {@code DocumentExtractedEntity} row holds. Lives in the
 * {@code event} package (an exposed package, unlike {@code domain}) — the
 * {@code ingest} module's {@code EntityExtractor} must reference this type
 * directly to build {@link ExtractedEntityPayload}, so it can't live in
 * {@code document.domain} without violating the module boundary (ADR 0002,
 * ADR 0003).
 */
public enum ExtractedEntityType {
    DATE,
    AMOUNT,
    IBAN,
    REFERENCE
}
