package fr.easywork.document.api;

import fr.easywork.document.dto.DocumentDto;
import fr.easywork.document.dto.PageResponse;
import fr.easywork.document.exception.DocumentNotFoundException;
import fr.easywork.document.service.DocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DocumentController.class)
class DocumentControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean DocumentService documentService;

    @Test
    void list_returns200_forAuthenticatedUser() throws Exception {
        var emptyPage = new PageResponse<DocumentDto>(
            List.of(), new PageResponse.PageMetadata(0, 25, 0, 0));
        when(documentService.list(any(), any())).thenReturn(emptyPage);

        mockMvc.perform(get("/api/v1/documents")
                .with(jwt().jwt(j -> j.subject("user-sub"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void get_returns404_whenDocumentNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(documentService.get(any(), any()))
            .thenThrow(new DocumentNotFoundException(id));

        mockMvc.perform(get("/api/v1/documents/" + id)
                .with(jwt().jwt(j -> j.subject("user-sub"))))
            .andExpect(status().isNotFound());
    }

    @Test
    void list_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/documents"))
            .andExpect(status().isUnauthorized());
    }
}
