package fr.easywork.document.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Payload for PATCH /api/v1/documents/{id}.
 * All fields are optional — only non-null values are applied.
 */
public record DocumentUpdateRequest(
        @Size(min = 1, max = 500) String title,
        LocalDate documentDate,
        UUID correspondentId,
        UUID documentTypeId,
        Set<UUID> tagIds
) {}
