package fr.easywork.document.api;

import fr.easywork.document.dto.ConfirmSuggestionRequest;
import fr.easywork.document.dto.DocumentClassificationSuggestionDto;
import fr.easywork.document.exception.SuggestionNotFoundException;
import fr.easywork.document.service.SuggestionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentSuggestionControllerTest {

    @InjectMocks DocumentSuggestionController controller;
    @Mock SuggestionService suggestionService;

    private static DocumentClassificationSuggestionDto dto(UUID id) {
        return new DocumentClassificationSuggestionDto(id, null, null, null, List.of(), null, null, null, null, null);
    }

    @Test
    void get_delegatesToService() {
        var jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("user-sub");
        UUID id = UUID.randomUUID();
        var expected = dto(id);
        when(suggestionService.getSuggestion(id, "user-sub")).thenReturn(expected);

        var result = controller.get(id, jwt);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void get_propagatesNotFound() {
        var jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("user-sub");
        UUID id = UUID.randomUUID();
        when(suggestionService.getSuggestion(id, "user-sub")).thenThrow(new SuggestionNotFoundException(id));

        assertThatThrownBy(() -> controller.get(id, jwt)).isInstanceOf(SuggestionNotFoundException.class);
    }

    @Test
    void confirm_delegatesToServiceWithRequestBody() {
        var jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("user-sub");
        UUID id = UUID.randomUUID();
        var expected = dto(id);
        var request = new ConfirmSuggestionRequest(true, true, false, Set.of());
        when(suggestionService.confirmSuggestion(id, "user-sub", request)).thenReturn(expected);

        var result = controller.confirm(id, request, jwt);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void reject_delegatesToService() {
        var jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("user-sub");
        UUID id = UUID.randomUUID();
        var expected = dto(id);
        when(suggestionService.rejectSuggestion(id, "user-sub")).thenReturn(expected);

        var result = controller.reject(id, jwt);

        assertThat(result).isEqualTo(expected);
    }
}
