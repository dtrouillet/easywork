package fr.easywork.document.repository;

import fr.easywork.document.domain.Document;
import fr.easywork.document.domain.DocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Page<Document> findByOwnerIdAndStatusNot(String ownerId, DocumentStatus status, Pageable pageable);

    Optional<Document> findByIdAndOwnerId(UUID id, String ownerId);

    Optional<Document> findByContentHashAndOwnerId(String contentHash, String ownerId);

    boolean existsByContentHashAndOwnerId(String contentHash, String ownerId);

    @Query("""
        SELECT d FROM Document d
        WHERE d.documentType.retentionDays IS NOT NULL
          AND d.status = 'READY'
          AND d.createdAt < :cutoff
        """)
    Page<Document> findExpiredDocuments(Instant cutoff, Pageable pageable);
}
