package fr.easywork.document.api;

import fr.easywork.document.dto.DocumentDto;
import fr.easywork.document.dto.DocumentSearchCriteria;
import fr.easywork.document.dto.PageResponse;
import fr.easywork.document.exception.DocumentNotFoundException;
import fr.easywork.document.service.DocumentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

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

    @Test
    void list_delegatesToServiceWithCriteria() {
        var jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("user-sub");

        var emptyPage = new PageResponse<DocumentDto>(
            List.of(), new PageResponse.PageMetadata(0, 25, 0, 0));
        when(documentService.list(eq("user-sub"), any(DocumentSearchCriteria.class), any()))
            .thenReturn(emptyPage);

        var result = controller.list(0, 25, null, null, null, null, null, jwt);

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
