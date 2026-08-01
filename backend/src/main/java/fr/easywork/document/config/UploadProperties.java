package fr.easywork.document.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

@ConfigurationProperties(prefix = "easywork.upload")
public record UploadProperties(
    long maxFileSizeBytes,
    Set<String> allowedMimeTypes
) {}
