package fr.easywork.document.api;

import fr.easywork.document.dto.CorrespondentDto;
import fr.easywork.document.service.CorrespondentService;
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
@RequestMapping("/api/v1/correspondents")
@RequiredArgsConstructor
@Tag(name = "Correspondents")
class CorrespondentController {

    private final CorrespondentService correspondentService;

    @GetMapping
    @Operation(summary = "List all correspondents")
    List<CorrespondentDto> list() {
        return correspondentService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a correspondent")
    @ApiResponse(responseCode = "201", description = "Correspondent created")
    @ApiResponse(responseCode = "409", description = "Name already exists")
    CorrespondentDto create(@RequestParam @NotBlank String name) {
        return correspondentService.create(name);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Rename a correspondent")
    @ApiResponse(responseCode = "200", description = "Correspondent updated")
    @ApiResponse(responseCode = "409", description = "Name already exists")
    CorrespondentDto update(@PathVariable UUID id, @RequestParam @NotBlank String name) {
        return correspondentService.update(id, name);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a correspondent")
    @ApiResponse(responseCode = "204", description = "Correspondent deleted")
    @ApiResponse(responseCode = "409", description = "Correspondent still referenced by documents")
    void delete(@PathVariable UUID id) {
        correspondentService.delete(id);
    }

    @PostMapping("/{id}/merge")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Merge a correspondent into another — reassigns its documents, then deletes it")
    @ApiResponse(responseCode = "204", description = "Correspondent merged")
    void merge(@PathVariable UUID id, @RequestParam UUID targetId) {
        correspondentService.merge(id, targetId);
    }
}
