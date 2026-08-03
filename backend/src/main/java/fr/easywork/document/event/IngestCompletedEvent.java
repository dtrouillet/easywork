package fr.easywork.document.event;

import org.springframework.modulith.events.Externalized;

import java.util.List;
import java.util.UUID;

/**
 * Published by the ingest module when OCR/extraction finishes.
 * Lives in the document package so ingest only depends on document (unidirectional).
 */
@Externalized("easywork.ingest.completed::#{#this.documentId}")
public record IngestCompletedEvent(
    UUID documentId,
    String contentHash,
    String extractedText,
    Integer pageCount,
    boolean ocrApplied,
    boolean success,
    String errorMessage,
    List<ExtractedEntityPayload> extractedEntities
) {}
