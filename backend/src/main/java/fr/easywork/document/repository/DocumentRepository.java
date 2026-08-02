package fr.easywork.document.repository;

import fr.easywork.document.domain.Document;
import fr.easywork.document.domain.DocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID>, JpaSpecificationExecutor<Document> {

    Page<Document> findByOwnerIdAndStatusNot(String ownerId, DocumentStatus status, Pageable pageable);

    Optional<Document> findByIdAndOwnerId(UUID id, String ownerId);

    Optional<Document> findByContentHashAndOwnerId(String contentHash, String ownerId);

    boolean existsByContentHashAndOwnerId(String contentHash, String ownerId);

    boolean existsByTagsId(UUID tagId);

    boolean existsByCorrespondentId(UUID correspondentId);

    boolean existsByDocumentTypeId(UUID documentTypeId);

    // Used by Tag/Correspondent/DocumentType merge to find documents to reassign
    // before deleting the merged-away entity.
    List<Document> findAllByTagsId(UUID tagId);

    List<Document> findAllByCorrespondentId(UUID correspondentId);

    List<Document> findAllByDocumentTypeId(UUID documentTypeId);

    /**
     * Returns READY documents whose retention period has elapsed.
     * Uses a native query so the per-row comparison (createdAt + retentionDays) is done in SQL,
     * avoiding a full table scan + Java-side filtering.
     * Compatible with H2 (MODE=PostgreSQL) and PostgreSQL 16+.
     */
    @Query(value = """
        SELECT d.* FROM document d
        JOIN document_type dt ON d.document_type_id = dt.id
        WHERE dt.retention_days IS NOT NULL
          AND d.status = 'READY'
          AND d.created_at + CAST(dt.retention_days || ' days' AS INTERVAL) < :now
        """, nativeQuery = true)
    Page<Document> findExpiredDocuments(Instant now, Pageable pageable);
}
