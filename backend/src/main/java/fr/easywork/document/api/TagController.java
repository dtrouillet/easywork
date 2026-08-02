package fr.easywork.document.api;

import fr.easywork.document.dto.TagDto;
import fr.easywork.document.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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

    private final TagService tagService;

    @GetMapping
    @Operation(summary = "List all tags")
    List<TagDto> list() {
        return tagService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a tag")
    @ApiResponse(responseCode = "201", description = "Tag created")
    @ApiResponse(responseCode = "409", description = "Name already exists")
    TagDto create(
            @RequestParam @NotBlank String name,
            @RequestParam(required = false) @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String color) {
        return tagService.create(name, color);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Rename a tag / change its color")
    @ApiResponse(responseCode = "200", description = "Tag updated")
    @ApiResponse(responseCode = "409", description = "Name already exists")
    TagDto update(
            @PathVariable UUID id,
            @RequestParam @NotBlank String name,
            @RequestParam(required = false) @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String color) {
        return tagService.update(id, name, color);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a tag")
    @ApiResponse(responseCode = "204", description = "Tag deleted")
    @ApiResponse(responseCode = "409", description = "Tag still referenced by documents")
    void delete(@PathVariable UUID id) {
        tagService.delete(id);
    }

    @PostMapping("/{id}/merge")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Merge a tag into another — reassigns its documents, then deletes it")
    @ApiResponse(responseCode = "204", description = "Tag merged")
    void merge(@PathVariable UUID id, @RequestParam UUID targetId) {
        tagService.merge(id, targetId);
    }
}
