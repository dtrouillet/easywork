package fr.easywork.document.api;

import fr.easywork.document.domain.Tag;
import fr.easywork.document.dto.TagDto;
import fr.easywork.document.mapper.DocumentMapper;
import fr.easywork.document.repository.TagRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
@Tag(name = "Tags")
class TagController {

    private final TagRepository tagRepository;
    private final DocumentMapper mapper;

    @GetMapping
    List<TagDto> list() {
        return tagRepository.findAll().stream().map(mapper::toDto).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TagDto create(@RequestParam @NotBlank String name) {
        return mapper.toDto(tagRepository.save(new fr.easywork.document.domain.Tag(name)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        tagRepository.deleteById(id);
    }
}
