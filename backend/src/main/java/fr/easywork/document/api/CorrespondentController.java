package fr.easywork.document.api;

import fr.easywork.document.domain.Correspondent;
import fr.easywork.document.dto.CorrespondentDto;
import fr.easywork.document.mapper.DocumentMapper;
import fr.easywork.document.repository.CorrespondentRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/correspondents")
@RequiredArgsConstructor
@Tag(name = "Correspondents")
class CorrespondentController {

    private final CorrespondentRepository correspondentRepository;
    private final DocumentMapper mapper;

    @GetMapping
    List<CorrespondentDto> list() {
        return correspondentRepository.findAll().stream().map(mapper::toDto).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CorrespondentDto create(@RequestParam @NotBlank String name) {
        return mapper.toDto(correspondentRepository.save(new Correspondent(name)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        correspondentRepository.deleteById(id);
    }
}
