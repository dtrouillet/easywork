package fr.easywork.document.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleEmptyFile_returns400() {
        var problem = handler.handleEmptyFile(new EmptyFileException());

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    void handleFileTooLarge_returns413() {
        var problem = handler.handleFileTooLarge(new FileTooLargeException(200L, 100L));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.value());
        assertThat(problem.getDetail()).contains("200").contains("100");
    }

    @Test
    void handleUnsupportedMimeType_returns415() {
        var problem = handler.handleUnsupportedMimeType(new UnsupportedMimeTypeException("image/gif"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value());
        assertThat(problem.getDetail()).contains("image/gif");
    }

    @Test
    void handleMaxUploadSizeExceeded_returns413() {
        var problem = handler.handleMaxUploadSizeExceeded(new MaxUploadSizeExceededException(1000L));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.value());
    }
}
