package fr.easywork.document.event;

import org.springframework.modulith.events.Externalized;

import java.util.UUID;

/**
 * Published when a document is stored and ready for ingest processing.
 * Externalized so that a separate ingest pod can consume it via AMQP.
 */
@Externalized("easywork.document.uploaded::#{#this.documentId}")
public record DocumentUploadedEvent(UUID documentId, String storageKey, String mimeType, String ownerId) {}
