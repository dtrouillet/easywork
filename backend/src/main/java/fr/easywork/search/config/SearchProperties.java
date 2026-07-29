package fr.easywork.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "easywork.search")
public record SearchProperties(String host, String apiKey) {}
