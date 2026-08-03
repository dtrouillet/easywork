package fr.easywork.document.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * Learned association between a classification signal (a correspondent or
 * document type, identified polymorphically by {@link #signalType}/
 * {@link #signalId} — no DB-level FK on {@code signalId}, see ADR 0003) and a
 * tag the user has confirmed for documents carrying that signal. Incremented
 * by {@code LearningService} on every manual save that sets both a
 * correspondent/type and tags; read back by {@code LearnedAssociationResolver}
 * to suggest the same tags on similar future documents.
 */
@Entity
@Table(
    name = "classification_signal_tag",
    uniqueConstraints = @UniqueConstraint(columnNames = {"signal_type", "signal_id", "tag_id"})
)
public class ClassificationSignalTagAssociation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "signal_type", nullable = false)
    private ClassificationSignalType signalType;

    @Column(name = "signal_id", nullable = false)
    private UUID signalId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tag_id", nullable = false)
    private Tag tag;

    @Column(name = "confirmation_count", nullable = false)
    private int confirmationCount;

    @Column(name = "last_confirmed_at", nullable = false)
    private Instant lastConfirmedAt;

    public ClassificationSignalTagAssociation() {}

    public ClassificationSignalTagAssociation(ClassificationSignalType signalType, UUID signalId, Tag tag) {
        this.signalType = signalType;
        this.signalId = signalId;
        this.tag = tag;
        this.confirmationCount = 0;
    }

    public void recordConfirmation() {
        this.confirmationCount++;
        this.lastConfirmedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public ClassificationSignalType getSignalType() { return signalType; }
    public void setSignalType(ClassificationSignalType signalType) { this.signalType = signalType; }

    public UUID getSignalId() { return signalId; }
    public void setSignalId(UUID signalId) { this.signalId = signalId; }

    public Tag getTag() { return tag; }
    public void setTag(Tag tag) { this.tag = tag; }

    public int getConfirmationCount() { return confirmationCount; }
    public void setConfirmationCount(int confirmationCount) { this.confirmationCount = confirmationCount; }

    public Instant getLastConfirmedAt() { return lastConfirmedAt; }
    public void setLastConfirmedAt(Instant lastConfirmedAt) { this.lastConfirmedAt = lastConfirmedAt; }
}
