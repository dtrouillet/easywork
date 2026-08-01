package fr.easywork.document.service;

import fr.easywork.document.config.UploadProperties;
import fr.easywork.document.exception.EmptyFileException;
import fr.easywork.document.exception.FileTooLargeException;
import fr.easywork.document.exception.UnsupportedMimeTypeException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadValidatorTest {

    private static final byte[] PDF_BYTES =
        "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] GIF_BYTES =
        "GIF87a".getBytes(StandardCharsets.US_ASCII);

    private final UploadProperties props =
        new UploadProperties(1_000_000L, Set.of("application/pdf"));
    private final UploadValidator validator = new UploadValidator(props);

    @Test
    void validate_acceptsPdf_andReturnsDetectedMimeType() {
        var file = new MockMultipartFile("file", "invoice.pdf", "application/pdf", PDF_BYTES);

        String detected = validator.validate(file);

        assertThat(detected).isEqualTo("application/pdf");
    }

    @Test
    void validate_rejectsEmptyFile() {
        var file = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> validator.validate(file))
            .isInstanceOf(EmptyFileException.class);
    }

    @Test
    void validate_rejectsOversizedFile() {
        var tinyLimit = new UploadProperties(10L, Set.of("application/pdf"));
        var tinyValidator = new UploadValidator(tinyLimit);
        var file = new MockMultipartFile("file", "invoice.pdf", "application/pdf", PDF_BYTES);

        assertThatThrownBy(() -> tinyValidator.validate(file))
            .isInstanceOf(FileTooLargeException.class);
    }

    @Test
    void validate_rejectsDisallowedMimeType() {
        var file = new MockMultipartFile("file", "picture.gif", "image/gif", GIF_BYTES);

        assertThatThrownBy(() -> validator.validate(file))
            .isInstanceOf(UnsupportedMimeTypeException.class)
            .hasMessageContaining("image/gif");
    }

    @Test
    void validate_ignoresClientSuppliedContentType_whenBytesDontMatch() {
        // Declared as a PDF, but the actual bytes are a GIF — the client-supplied
        // content-type header must not be trusted.
        var file = new MockMultipartFile("file", "fake.pdf", "application/pdf", GIF_BYTES);

        assertThatThrownBy(() -> validator.validate(file))
            .isInstanceOf(UnsupportedMimeTypeException.class)
            .hasMessageContaining("image/gif");
    }
}
