package fr.easywork.document.api;

import fr.easywork.document.dto.DocumentTypeDto;
import fr.easywork.document.service.DocumentTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/document-types")
@RequiredArgsConstructor
@Tag(name = "Document types")
class DocumentTypeController {

    private final DocumentTypeService documentTypeService;

    @GetMapping
    @Operation(summary = "List all document types")
    List<DocumentTypeDto> list() {
        return documentTypeService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a document type")
    @ApiResponse(responseCode = "201", description = "Document type created")
    @ApiResponse(responseCode = "409", description = "Name already exists")
    DocumentTypeDto create(
            @RequestParam @NotBlank String name,
            @RequestParam(required = false) Integer retentionDays) {
        return documentTypeService.create(name, retentionDays);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a document type")
    @ApiResponse(responseCode = "204", description = "Document type deleted")
    @ApiResponse(responseCode = "409", description = "Document type still referenced by documents")
    void delete(@PathVariable UUID id) {
        documentTypeService.delete(id);
    }
}
