package fr.easywork.document.exception;

import java.util.UUID;

public class SuggestionNotFoundException extends RuntimeException {
    public SuggestionNotFoundException(UUID documentId) {
        super("No classification suggestion for document: " + documentId);
    }
}
