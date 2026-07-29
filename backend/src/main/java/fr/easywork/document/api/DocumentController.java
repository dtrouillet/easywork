package fr.easywork.document.api;

import fr.easywork.document.dto.DocumentDto;
import fr.easywork.document.dto.PageResponse;
import fr.easywork.document.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Tag(name = "Documents")
class DocumentController {

    private final DocumentService documentService;

    @GetMapping
    @Operation(summary = "List documents (paginated)")
    PageResponse<DocumentDto> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @AuthenticationPrincipal Jwt jwt) {
        return documentService.list(
            jwt.getSubject(),
            PageRequest.of(page, size, Sort.by("createdAt").descending()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get document by id")
    DocumentDto get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return documentService.get(id, jwt.getSubject());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload a new document")
    DocumentDto upload(@RequestParam("file") MultipartFile file,
                       @AuthenticationPrincipal Jwt jwt) {
        return documentService.upload(file, jwt.getSubject());
    }

    @PostMapping("/{id}/trash")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Move document to trash")
    void trash(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        documentService.trash(id, jwt.getSubject());
    }

    @PostMapping("/{id}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Archive document")
    void archive(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        documentService.archive(id, jwt.getSubject());
    }

    @PostMapping("/{id}/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Restore document from trash or archive")
    void restore(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        documentService.restore(id, jwt.getSubject());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Permanently delete document (GDPR erasure)")
    void delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        documentService.permanentDelete(id, jwt.getSubject());
    }
}
