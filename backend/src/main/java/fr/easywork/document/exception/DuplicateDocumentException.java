package fr.easywork.document.exception;

import java.util.UUID;

public class DuplicateDocumentException extends RuntimeException {
    public DuplicateDocumentException(UUID existingId) {
        super("Document already exists with id: " + existingId);
    }
}
