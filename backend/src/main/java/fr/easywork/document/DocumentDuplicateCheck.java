package fr.easywork.document;

import java.util.UUID;

/** Port: lets ingest short-circuit extraction/OCR for a file already stored elsewhere. */
@FunctionalInterface
public interface DocumentDuplicateCheck {
    boolean existsDuplicate(String contentHash, String ownerId, UUID excludingDocumentId);
}
