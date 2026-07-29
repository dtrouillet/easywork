package fr.easywork.document.event;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Published when a document reaches READY status — consumed by the search module. */
public record DocumentReadyEvent(
    UUID documentId,
    String title,
    String extractedText,
    String mimeType,
    LocalDate documentDate,
    List<String> tags,
    String correspondent,
    String documentType,
    String ownerId
) {}
