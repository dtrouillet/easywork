package fr.easywork.document.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A pending (or resolved) auto-classification suggestion for one document —
 * the review step the old, silent {@code DocumentClassifier} never had (ADR
 * 0003). One row per document ({@code documentId} is the primary key, set
 * explicitly rather than generated, since a suggestion always belongs to an
 * already-persisted {@link Document}). Confirming or rejecting never mutates
 * this row's correspondent/type/tag references — only {@link #status} and the
 * matching timestamp change; the actual {@link Document} fields are updated
 * separately by {@code SuggestionService}.
 */
@Entity
@Table(name = "document_classification_suggestion")
public class DocumentClassificationSuggestion {

    @Id
    @Column(name = "document_id")
    private UUID documentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suggested_correspondent_id")
    private Correspondent suggestedCorrespondent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suggested_document_type_id")
    private DocumentType suggestedDocumentType;

    @Column(name = "suggested_document_date")
    private LocalDate suggestedDocumentDate;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "document_classification_suggestion_tag",
        joinColumns = @JoinColumn(name = "document_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<Tag> suggestedTags = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SuggestionSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SuggestionStatus status = SuggestionStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    public DocumentClassificationSuggestion() {}

    public DocumentClassificationSuggestion(UUID documentId, SuggestionSource source) {
        this.documentId = documentId;
        this.source = source;
        this.createdAt = Instant.now();
        this.status = SuggestionStatus.PENDING;
    }

    /** Resets a resolved suggestion back to PENDING with fresh candidate values (regeneration via /reclassify). */
    // null is the correct "not yet resolved" value for confirmedAt/rejectedAt — not a code smell.
    @SuppressWarnings("PMD.NullAssignment")
    public void regenerate(SuggestionSource source) {
        this.source = source;
        this.status = SuggestionStatus.PENDING;
        this.createdAt = Instant.now();
        this.confirmedAt = null;
        this.rejectedAt = null;
    }

    public void confirm() {
        this.status = SuggestionStatus.CONFIRMED;
        this.confirmedAt = Instant.now();
    }

    public void reject() {
        this.status = SuggestionStatus.REJECTED;
        this.rejectedAt = Instant.now();
    }

    public boolean isEmpty() {
        return suggestedCorrespondent == null
            && suggestedDocumentType == null
            && suggestedDocumentDate == null
            && suggestedTags.isEmpty();
    }

    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }

    public Correspondent getSuggestedCorrespondent() { return suggestedCorrespondent; }
    public void setSuggestedCorrespondent(Correspondent suggestedCorrespondent) {
        this.suggestedCorrespondent = suggestedCorrespondent;
    }

    public DocumentType getSuggestedDocumentType() { return suggestedDocumentType; }
    public void setSuggestedDocumentType(DocumentType suggestedDocumentType) {
        this.suggestedDocumentType = suggestedDocumentType;
    }

    public LocalDate getSuggestedDocumentDate() { return suggestedDocumentDate; }
    public void setSuggestedDocumentDate(LocalDate suggestedDocumentDate) {
        this.suggestedDocumentDate = suggestedDocumentDate;
    }

    public Set<Tag> getSuggestedTags() { return suggestedTags; }
    public void setSuggestedTags(Set<Tag> suggestedTags) { this.suggestedTags = suggestedTags; }

    public SuggestionSource getSource() { return source; }
    public void setSource(SuggestionSource source) { this.source = source; }

    public SuggestionStatus getStatus() { return status; }
    public void setStatus(SuggestionStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(Instant confirmedAt) { this.confirmedAt = confirmedAt; }

    public Instant getRejectedAt() { return rejectedAt; }
    public void setRejectedAt(Instant rejectedAt) { this.rejectedAt = rejectedAt; }
}
