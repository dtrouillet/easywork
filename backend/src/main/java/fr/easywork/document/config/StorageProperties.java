package fr.easywork.document.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "easywork.storage")
public record StorageProperties(
    String endpoint,
    String accessKey,
    String secretKey,
    String bucket
) {}
