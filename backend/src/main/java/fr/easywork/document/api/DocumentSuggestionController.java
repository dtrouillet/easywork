package fr.easywork.document.api;

import fr.easywork.document.dto.ConfirmSuggestionRequest;
import fr.easywork.document.dto.DocumentClassificationSuggestionDto;
import fr.easywork.document.service.SuggestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** ADR 0003: review/confirm/reject an auto-classification suggestion. */
@RestController
@RequestMapping("/api/v1/documents/{id}/suggestion")
@RequiredArgsConstructor
@Tag(name = "Classification suggestions")
class DocumentSuggestionController {

    private final SuggestionService suggestionService;

    @GetMapping
    @Operation(summary = "Get the pending (or last resolved) classification suggestion for a document")
    @ApiResponse(responseCode = "200", description = "Suggestion found")
    @ApiResponse(responseCode = "404", description = "Document or suggestion not found")
    DocumentClassificationSuggestionDto get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return suggestionService.getSuggestion(id, jwt.getSubject());
    }

    @PostMapping("/confirm")
    @Operation(summary = "Accept some or all of a suggestion's fields, applying them to the document")
    @ApiResponse(responseCode = "200", description = "Suggestion confirmed")
    @ApiResponse(responseCode = "404", description = "Document or suggestion not found")
    DocumentClassificationSuggestionDto confirm(
            @PathVariable UUID id,
            @RequestBody ConfirmSuggestionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return suggestionService.confirmSuggestion(id, jwt.getSubject(), request);
    }

    @PostMapping("/reject")
    @Operation(summary = "Dismiss a suggestion without applying anything to the document")
    @ApiResponse(responseCode = "200", description = "Suggestion rejected")
    @ApiResponse(responseCode = "404", description = "Document or suggestion not found")
    DocumentClassificationSuggestionDto reject(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return suggestionService.rejectSuggestion(id, jwt.getSubject());
    }
}
