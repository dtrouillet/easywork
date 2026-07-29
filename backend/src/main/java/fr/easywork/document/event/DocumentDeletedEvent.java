package fr.easywork.document.event;

import java.util.UUID;

/** Published when a document is permanently deleted — consumed by the search module. */
public record DocumentDeletedEvent(UUID documentId) {}
