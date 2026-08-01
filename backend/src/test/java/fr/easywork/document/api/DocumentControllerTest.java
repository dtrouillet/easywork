package fr.easywork.document.api;

import fr.easywork.document.domain.DocumentStatus;
import fr.easywork.document.dto.DocumentDto;
import fr.easywork.document.dto.DocumentSearchCriteria;
import fr.easywork.document.dto.PageResponse;
import fr.easywork.document.exception.DocumentNotFoundException;
import fr.easywork.document.exception.UnsupportedMimeTypeException;
import fr.easywork.document.service.DocumentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentControllerTest {

    @InjectMocks DocumentController controller;
    @Mock DocumentService documentService;

    // --- upload ---

    @Test
    void upload_delegatesToServiceAndReturnsCreatedDto() {
        var jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("user-sub");
        var file = new MockMultipartFile(
            "file", "invoice.pdf", "application/pdf", "content".getBytes(StandardCharsets.UTF_8));
        var expected = new DocumentDto(
            UUID.randomUUID(), "invoice.pdf", DocumentStatus.RECEIVED, "invoice.pdf",
            "application/pdf", 7L, null, false, null, List.of(), null, null, null, null);
        when(documentService.upload(file, "user-sub")).thenReturn(expected);

        var result = controller.upload(file, jwt);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void upload_propagatesServiceException() {
        var jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("user-sub");
        var file = new MockMultipartFile(
            "file", "invoice.exe", "application/octet-stream", "content".getBytes(StandardCharsets.UTF_8));
        when(documentService.upload(file, "user-sub"))
            .thenThrow(new UnsupportedMimeTypeException("application/x-msdownload"));

        assertThatThrownBy(() -> controller.upload(file, jwt))
            .isInstanceOf(UnsupportedMimeTypeException.class);
    }

    @Test
    void list_delegatesToServiceWithCriteria() {
        var jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("user-sub");

        var emptyPage = new PageResponse<DocumentDto>(
            List.of(), new PageResponse.PageMetadata(0, 25, 0, 0));
        when(documentService.list(eq("user-sub"), any(DocumentSearchCriteria.class), any()))
            .thenReturn(emptyPage);

        var result = controller.list(0, 25, null, null, null, null, null, null, jwt);

        assertThat(result.content()).isEmpty();
        assertThat(result.page().totalElements()).isZero();
    }

    @Test
    void get_throwsNotFound_whenDocumentMissing() {
        var jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("user-sub");
        UUID id = UUID.randomUUID();
        when(documentService.get(any(), any()))
            .thenThrow(new DocumentNotFoundException(id));

        assertThatThrownBy(() -> controller.get(id, jwt))
            .isInstanceOf(DocumentNotFoundException.class);
    }
}
