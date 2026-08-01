package fr.easywork.document.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FilenameSanitizerTest {

    @Test
    void sanitize_stripsPathTraversalSegments() {
        String result = FilenameSanitizer.sanitize("../../etc/passwd");

        assertThat(result).doesNotContain("/").doesNotContain("..");
    }

    @Test
    void sanitize_stripsWindowsPathSegments() {
        String result = FilenameSanitizer.sanitize("..\\..\\win.ini");

        assertThat(result).doesNotContain("\\").doesNotContain("..");
    }

    @Test
    void sanitize_replacesUnsafeCharacters() {
        String result = FilenameSanitizer.sanitize("my <bad>:\"file\"|?*.pdf");

        assertThat(result).matches("[A-Za-z0-9._-]+");
    }

    @Test
    void sanitize_preservesSafeFilename() {
        String result = FilenameSanitizer.sanitize("invoice-2026.pdf");

        assertThat(result).isEqualTo("invoice-2026.pdf");
    }

    @Test
    void sanitize_capsLength() {
        String longName = "a".repeat(500) + ".pdf";

        String result = FilenameSanitizer.sanitize(longName);

        assertThat(result.length()).isLessThanOrEqualTo(150);
        assertThat(result).endsWith(".pdf");
    }

    @Test
    void sanitize_fallsBackToDefault_whenResultEmpty() {
        String result = FilenameSanitizer.sanitize("???");

        assertThat(result).isEqualTo("file");
    }

    @Test
    void sanitize_fallsBackToDefault_whenOnlyDots() {
        String result = FilenameSanitizer.sanitize("...");

        assertThat(result).isEqualTo("file");
    }
}
