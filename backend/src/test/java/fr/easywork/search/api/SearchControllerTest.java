package fr.easywork.search.api;

import fr.easywork.search.service.SearchIndexService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchControllerTest {

    @Mock SearchIndexService searchIndexService;
    @InjectMocks SearchController controller;

    @Test
    void search_delegatesToServiceWithOwnerFromJwt() {
        var jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("user-sub");
        Object expected = new Object();
        when(searchIndexService.search("invoice", "user-sub", 1, 10)).thenReturn(expected);

        Object result = controller.search("invoice", 1, 10, jwt);

        assertThat(result).isSameAs(expected);
        verify(searchIndexService).search("invoice", "user-sub", 1, 10);
    }

    @Test
    void search_usesDefaultPageAndSize() {
        var jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("user-sub");

        controller.search("invoice", 0, 25, jwt);

        verify(searchIndexService).search("invoice", "user-sub", 0, 25);
    }
}
