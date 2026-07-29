package fr.easywork.ingest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "easywork.ingest")
public record IngestProperties(String tessdataPath, String ocrLanguages) {}
