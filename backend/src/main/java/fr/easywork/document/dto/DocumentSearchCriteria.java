package fr.easywork.document.dto;

import fr.easywork.document.domain.DocumentStatus;

import java.util.UUID;

/**
 * Query parameters for the document list endpoint.
 * All fields are optional — null means "no filter on this dimension".
 */
public record DocumentSearchCriteria(
        DocumentStatus status,
        UUID tagId,
        UUID correspondentId,
        UUID documentTypeId,
        String titleContains,
        Integer documentYear
) {}
