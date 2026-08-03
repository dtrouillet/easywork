package fr.easywork.document.repository;

import fr.easywork.document.domain.DocumentExtractedEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentExtractedEntityRepository extends JpaRepository<DocumentExtractedEntity, UUID> {

    List<DocumentExtractedEntity> findByDocumentId(UUID documentId);
}
