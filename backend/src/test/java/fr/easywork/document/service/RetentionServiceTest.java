package fr.easywork.document.service;

import fr.easywork.document.domain.Document;
import fr.easywork.document.domain.DocumentStatus;
import fr.easywork.document.domain.DocumentType;
import fr.easywork.document.repository.DocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetentionServiceTest {

    @Mock DocumentRepository documentRepository;

    @InjectMocks RetentionService retentionService;

    @Test
    void applyRetentionPolicies_trashesExpiredDocuments() {
        Document doc = expiredDocument();
        var page = new PageImpl<>(List.of(doc));
        when(documentRepository.findExpiredDocuments(any(Instant.class), any(Pageable.class)))
            .thenReturn(page)
            .thenReturn(new PageImpl<>(List.of())); // second call returns empty (no more pages)

        retentionService.applyRetentionPolicies();

        verify(documentRepository).save(argThat(d -> d.getStatus() == DocumentStatus.TRASH));
        assertThat(doc.getTrashedAt()).isNotNull();
    }

    @Test
    void applyRetentionPolicies_isIdempotent_whenNoExpiredDocuments() {
        when(documentRepository.findExpiredDocuments(any(Instant.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));

        retentionService.applyRetentionPolicies();

        verify(documentRepository, never()).save(any());
    }

    @Test
    void trashPage_skipsDocument_ifAlreadyTransitioned() {
        Document doc = new Document();
        doc.setId(UUID.randomUUID());
        doc.setStatus(DocumentStatus.TRASH); // already trashed — TRASH → TRASH is invalid

        var page = new PageImpl<>(List.of(doc));

        // Should not throw; the IllegalStateException from transitionTo is caught internally
        int count = retentionService.trashPage(page, Instant.now());

        assertThat(count).isZero();
        verify(documentRepository, never()).save(any());
    }

    private static Document expiredDocument() {
        DocumentType type = new DocumentType("Facture");
        type.setRetentionDays(30);

        Document doc = new Document();
        doc.setId(UUID.randomUUID());
        doc.setStatus(DocumentStatus.READY);
        doc.setDocumentType(type);
        return doc;
    }
}
