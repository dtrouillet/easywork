package fr.easywork.document.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "easywork.rabbit")
public record RabbitTopologyProperties(
    String documentUploadedExchange,
    String documentUploadedQueue,
    String ingestCompletedExchange,
    String ingestCompletedQueue
) {}
