package fr.easywork.document.dto;

import fr.easywork.document.domain.SuggestionSource;
import fr.easywork.document.domain.SuggestionStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** ADR 0003: the reviewable output of auto-classification, pending user confirmation. */
public record DocumentClassificationSuggestionDto(
    UUID documentId,
    CorrespondentDto suggestedCorrespondent,
    DocumentTypeDto suggestedDocumentType,
    LocalDate suggestedDocumentDate,
    List<TagDto> suggestedTags,
    SuggestionSource source,
    SuggestionStatus status,
    Instant createdAt,
    Instant confirmedAt,
    Instant rejectedAt
) {}
