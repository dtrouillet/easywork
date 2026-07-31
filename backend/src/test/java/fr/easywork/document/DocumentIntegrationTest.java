package fr.easywork.document;

import fr.easywork.AbstractIntegrationTest;
import fr.easywork.document.domain.Document;
import fr.easywork.document.domain.DocumentStatus;
import fr.easywork.document.domain.Tag;
import fr.easywork.document.dto.DocumentSearchCriteria;
import fr.easywork.document.dto.DocumentUpdateRequest;
import fr.easywork.document.dto.PageResponse;
import fr.easywork.document.dto.DocumentDto;
import fr.easywork.document.repository.DocumentRepository;
import fr.easywork.document.repository.TagRepository;
import fr.easywork.document.service.DocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-cycle integration test: real PostgreSQL (Testcontainers), real MinIO.
 * Verifies ADR 0001: permanentDelete removes the DB row and audit entries
 * contain no personal data columns.
 */
class DocumentIntegrationTest extends AbstractIntegrationTest {

    @Autowired DocumentService documentService;
    @Autowired DocumentRepository documentRepository;
    @Autowired TagRepository tagRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    private static final String OWNER = "integration-test-user";

    @Test
    @WithMockUser
    void permanentDelete_removesRowFromDB_andAuditHasNoPersonalDataColumns() {
        // Arrange: create a document directly in TRASH state (bypassing ingest pipeline)
        Document doc = new Document();
        doc.setTitle("Confidentiel Invoice");
        doc.setOriginalFilename("invoice.pdf");
        doc.setMimeType("application/pdf");
        doc.setFileSize(1024L);
        doc.setOwnerId(OWNER);
        doc.setStatus(DocumentStatus.TRASH);
        documentRepository.save(doc);
        UUID docId = doc.getId();

        // Act
        documentService.permanentDelete(docId, OWNER);

        // Assert: row is gone from the main table
        assertThat(documentRepository.findById(docId)).isEmpty();

        // Assert: audit table has no personal-data columns (dropped in V1_3)
        List<Map<String, Object>> auditRows = jdbcTemplate.queryForList(
            "SELECT * FROM document_aud WHERE id = ?", docId);
        for (Map<String, Object> row : auditRows) {
            assertThat(row).doesNotContainKey("title");
            assertThat(row).doesNotContainKey("owner_id");
            assertThat(row).doesNotContainKey("original_filename");
            assertThat(row).doesNotContainKey("extracted_text");
        }
    }

    @Test
    @WithMockUser
    void update_changesMetadata_andDocumentIsPersisted() {
        // Arrange
        Document doc = new Document();
        doc.setTitle("Old Title");
        doc.setOriginalFilename("doc.pdf");
        doc.setMimeType("application/pdf");
        doc.setOwnerId(OWNER);
        doc.setStatus(DocumentStatus.READY);
        documentRepository.save(doc);
        UUID docId = doc.getId();

        // Act
        documentService.update(docId, OWNER, new DocumentUpdateRequest("New Title", null, null, null, null));

        // Assert
        Document updated = documentRepository.findById(docId).orElseThrow();
        assertThat(updated.getTitle()).isEqualTo("New Title");
    }

    @Test
    @WithMockUser
    void trash_then_restore_cycleworks() {
        Document doc = new Document();
        doc.setTitle("Cycle test");
        doc.setOriginalFilename("doc.pdf");
        doc.setMimeType("application/pdf");
        doc.setOwnerId(OWNER);
        doc.setStatus(DocumentStatus.READY);
        documentRepository.save(doc);
        UUID docId = doc.getId();

        documentService.trash(docId, OWNER);
        assertThat(documentRepository.findById(docId).orElseThrow().getStatus())
            .isEqualTo(DocumentStatus.TRASH);

        documentService.restore(docId, OWNER);
        assertThat(documentRepository.findById(docId).orElseThrow().getStatus())
            .isEqualTo(DocumentStatus.READY);
    }

    @Test
    @WithMockUser
    void list_filtersDeletedDocuments_andIsolatesOwners() {
        Document own = readyDoc("filter-owner", "Filter Doc");
        Document other = readyDoc("other-owner", "Other Doc");
        Document deleted = readyDoc("filter-owner", "Deleted Doc");
        deleted.setStatus(DocumentStatus.DELETED);
        documentRepository.saveAll(List.of(own, other, deleted));

        var criteria = new DocumentSearchCriteria(null, null, null, null, null);
        PageResponse<DocumentDto> result = documentService.list(
            "filter-owner", criteria, PageRequest.of(0, 25));

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).title()).isEqualTo("Filter Doc");
    }

    @Test
    @WithMockUser
    void list_filtersByTagId() {
        Tag tag = tagRepository.save(new Tag("finance"));

        Document tagged = readyDoc("tag-owner", "Invoice EDF");
        tagged.setTags(Set.of(tag));
        Document untagged = readyDoc("tag-owner", "Contract Notaire");
        documentRepository.saveAll(List.of(tagged, untagged));

        var criteria = new DocumentSearchCriteria(null, tag.getId(), null, null, null);
        PageResponse<DocumentDto> result = documentService.list(
            "tag-owner", criteria, PageRequest.of(0, 25));

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).title()).isEqualTo("Invoice EDF");
    }

    @Test
    @WithMockUser
    void list_filtersByTitle() {
        documentRepository.saveAll(List.of(
            readyDoc("search-owner", "Facture EDF 2026"),
            readyDoc("search-owner", "Contrat location")));

        var criteria = new DocumentSearchCriteria(null, null, null, null, "edf");
        PageResponse<DocumentDto> result = documentService.list(
            "search-owner", criteria, PageRequest.of(0, 25));

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).title()).isEqualTo("Facture EDF 2026");
    }

    private Document readyDoc(String ownerId, String title) {
        Document doc = new Document();
        doc.setTitle(title);
        doc.setOriginalFilename(title.replaceAll(" ", "_") + ".pdf");
        doc.setMimeType("application/pdf");
        doc.setOwnerId(ownerId);
        doc.setStatus(DocumentStatus.READY);
        return doc;
    }
}
