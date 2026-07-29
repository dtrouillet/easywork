package fr.easywork.document.dto;

import java.util.UUID;

public record DocumentTypeDto(UUID id, String name, Integer retentionDays) {}
