package fr.easywork.document.service;

import java.util.regex.Pattern;

/**
 * Produces a safe MinIO object-key segment from a client-supplied filename.
 * Guards against path traversal ("../../etc/passwd") and header/shell-unsafe characters —
 * the original filename is still preserved verbatim for display/download.
 */
final class FilenameSanitizer {

    private static final int MAX_LENGTH = 150;
    private static final String DEFAULT_NAME = "file";
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x1F\\x7F]");
    private static final Pattern UNSAFE_CHARS = Pattern.compile("[^A-Za-z0-9._-]");
    private static final Pattern LEADING_DOTS = Pattern.compile("^\\.+");
    private static final Pattern HAS_CONTENT = Pattern.compile("[A-Za-z0-9]");

    private FilenameSanitizer() {
    }

    static String sanitize(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return DEFAULT_NAME;
        }

        String lastSegment = originalFilename.replace('\\', '/');
        lastSegment = lastSegment.substring(lastSegment.lastIndexOf('/') + 1);

        String cleaned = CONTROL_CHARS.matcher(lastSegment).replaceAll("");
        cleaned = UNSAFE_CHARS.matcher(cleaned).replaceAll("_");
        cleaned = LEADING_DOTS.matcher(cleaned).replaceAll("");
        cleaned = capLength(cleaned);

        return HAS_CONTENT.matcher(cleaned).find() ? cleaned : DEFAULT_NAME;
    }

    private static String capLength(String filename) {
        if (filename.length() <= MAX_LENGTH) {
            return filename;
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0 && filename.length() - dotIndex <= 20) {
            String extension = filename.substring(dotIndex);
            String base = filename.substring(0, dotIndex);
            int baseMax = MAX_LENGTH - extension.length();
            if (baseMax > 0) {
                return base.substring(0, Math.min(base.length(), baseMax)) + extension;
            }
        }
        return filename.substring(0, MAX_LENGTH);
    }
}
