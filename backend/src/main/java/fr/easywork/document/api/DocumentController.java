package fr.easywork.document.api;

import fr.easywork.document.domain.DocumentStatus;
import fr.easywork.document.dto.DocumentDto;
import fr.easywork.document.dto.DocumentSearchCriteria;
import fr.easywork.document.dto.DocumentUpdateRequest;
import fr.easywork.document.dto.PageResponse;
import fr.easywork.document.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Tag(name = "Documents")
class DocumentController {

    private final DocumentService documentService;

    @GetMapping
    @Operation(summary = "List documents with optional filters (paginated)")
    @ApiResponse(responseCode = "200", description = "Page of documents")
    PageResponse<DocumentDto> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @Parameter(description = "Filter by status") @RequestParam(required = false) DocumentStatus status,
            @Parameter(description = "Filter by tag id") @RequestParam(required = false) UUID tagId,
            @Parameter(description = "Filter by correspondent id") @RequestParam(required = false) UUID correspondentId,
            @Parameter(description = "Filter by document type id") @RequestParam(required = false) UUID documentTypeId,
            @Parameter(description = "Search in title") @RequestParam(required = false) String q,
            @AuthenticationPrincipal Jwt jwt) {
        DocumentSearchCriteria criteria = new DocumentSearchCriteria(status, tagId, correspondentId, documentTypeId, q);
        return documentService.list(
            jwt.getSubject(), criteria,
            PageRequest.of(page, size, Sort.by("createdAt").descending()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get document by id")
    @ApiResponse(responseCode = "200", description = "Document found")
    @ApiResponse(responseCode = "404", description = "Document not found")
    DocumentDto get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return documentService.get(id, jwt.getSubject());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload a new document")
    @ApiResponse(responseCode = "201", description = "Document created")
    @ApiResponse(responseCode = "413", description = "File exceeds the maximum allowed size")
    @ApiResponse(responseCode = "415", description = "Unsupported file type")
    DocumentDto upload(@RequestParam("file") MultipartFile file,
                       @AuthenticationPrincipal Jwt jwt) {
        return documentService.upload(file, jwt.getSubject());
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update document metadata (title, date, tags, correspondent, type)")
    @ApiResponse(responseCode = "200", description = "Document updated")
    @ApiResponse(responseCode = "404", description = "Document not found")
    DocumentDto update(@PathVariable UUID id,
                       @Valid @RequestBody DocumentUpdateRequest request,
                       @AuthenticationPrincipal Jwt jwt) {
        return documentService.update(id, jwt.getSubject(), request);
    }

    @GetMapping("/{id}/file")
    @Operation(summary = "Download original file")
    @ApiResponse(responseCode = "200", description = "File content")
    @ApiResponse(responseCode = "404", description = "Document not found")
    ResponseEntity<InputStreamResource> download(@PathVariable UUID id,
                                                 @AuthenticationPrincipal Jwt jwt) {
        DocumentDto doc = documentService.get(id, jwt.getSubject());
        InputStream stream = documentService.download(id, jwt.getSubject());
        ContentDisposition disposition = ContentDisposition.attachment()
            .filename(doc.originalFilename(), StandardCharsets.UTF_8)
            .build();
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(doc.mimeType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .body(new InputStreamResource(stream));
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
    @ApiResponse(responseCode = "204", description = "Document erased")
    void delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        documentService.permanentDelete(id, jwt.getSubject());
    }
}
