package fr.easywork.document.api;

import fr.easywork.document.domain.DocumentType;
import fr.easywork.document.dto.DocumentTypeDto;
import fr.easywork.document.mapper.DocumentMapper;
import fr.easywork.document.repository.DocumentTypeRepository;
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

    private final DocumentTypeRepository documentTypeRepository;
    private final DocumentMapper mapper;

    @GetMapping
    List<DocumentTypeDto> list() {
        return documentTypeRepository.findAll().stream().map(mapper::toDto).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    DocumentTypeDto create(@RequestParam @NotBlank String name,
                           @RequestParam(required = false) Integer retentionDays) {
        DocumentType type = new DocumentType(name);
        type.setRetentionDays(retentionDays);
        return mapper.toDto(documentTypeRepository.save(type));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        documentTypeRepository.deleteById(id);
    }
}
