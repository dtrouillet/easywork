package fr.easywork.ingest.pipeline;

record ExtractionResult(String text, String detectedMimeType, boolean requiresOcr) {}
