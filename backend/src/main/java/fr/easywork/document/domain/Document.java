package fr.easywork.document.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.envers.Audited;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Audited
@EntityListeners(AuditingEntityListener.class)
@Table(name = "document")
@Getter
@Setter
@NoArgsConstructor
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String originalFilename;

    @Column(nullable = false)
    private String mimeType;

    private Long fileSize;

    @Column(unique = true)
    private String storageKey;

    private String contentHash;

    @Column(columnDefinition = "TEXT")
    private String extractedText;

    private Integer pageCount;

    private LocalDate documentDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status = DocumentStatus.RECEIVED;

    private boolean ocrApplied;

    @Column(nullable = false)
    private String ownerId;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "document_tag",
        joinColumns = @JoinColumn(name = "document_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<Tag> tags = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "correspondent_id")
    private Correspondent correspondent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_type_id")
    private DocumentType documentType;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    private Instant archivedAt;
    private Instant trashedAt;
    private Instant deletedAt;

    public void transitionTo(DocumentStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateException(
                "Cannot transition document " + id + " from " + status + " to " + next);
        }
        this.status = next;
    }
}
