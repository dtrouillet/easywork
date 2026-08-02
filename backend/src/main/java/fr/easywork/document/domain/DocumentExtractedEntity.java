package fr.easywork.document.domain;

import fr.easywork.document.event.ExtractedEntityType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * One value (date, amount, IBAN, reference number) parsed deterministically
 * from a document's extracted text during ingest (ADR 0003). Financial/personal
 * data: intentionally never {@code @Audited} (no Envers revision history at
 * all, per ADR 0001's principle) and cascade-deleted with the document.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "document_extracted_entity")
public class DocumentExtractedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false)
    private ExtractedEntityType entityType;

    @Column(name = "raw_value", nullable = false)
    private String rawValue;

    @Column(name = "normalized_value")
    private String normalizedValue;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public DocumentExtractedEntity() {}

    public DocumentExtractedEntity(
            Document document, ExtractedEntityType entityType, String rawValue, String normalizedValue) {
        this.document = document;
        this.entityType = entityType;
        this.rawValue = rawValue;
        this.normalizedValue = normalizedValue;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Document getDocument() { return document; }
    public void setDocument(Document document) { this.document = document; }

    public ExtractedEntityType getEntityType() { return entityType; }
    public void setEntityType(ExtractedEntityType entityType) { this.entityType = entityType; }

    public String getRawValue() { return rawValue; }
    public void setRawValue(String rawValue) { this.rawValue = rawValue; }

    public String getNormalizedValue() { return normalizedValue; }
    public void setNormalizedValue(String normalizedValue) { this.normalizedValue = normalizedValue; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
