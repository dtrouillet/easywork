package fr.easywork.document.dto;

import fr.easywork.document.domain.DocumentStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DocumentDto(
    UUID id,
    String title,
    DocumentStatus status,
    String originalFilename,
    String mimeType,
    Long fileSize,
    Integer pageCount,
    boolean ocrApplied,
    String lastIngestError,
    LocalDate documentDate,
    List<TagDto> tags,
    CorrespondentDto correspondent,
    DocumentTypeDto documentType,
    Instant createdAt,
    Instant updatedAt
) {}
