package fr.easywork.document.repository;

import fr.easywork.document.domain.Document;
import fr.easywork.document.domain.DocumentStatus;
import fr.easywork.document.domain.Tag;
import fr.easywork.document.dto.DocumentSearchCriteria;
import jakarta.persistence.criteria.Join;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public final class DocumentSpecifications {

    private DocumentSpecifications() {}

    public static Specification<Document> forOwner(String ownerId) {
        return (root, query, cb) -> cb.equal(root.get("ownerId"), ownerId);
    }

    public static Specification<Document> withStatus(DocumentStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Document> notPermanentlyDeleted() {
        return (root, query, cb) -> cb.notEqual(root.get("status"), DocumentStatus.DELETED);
    }

    public static Specification<Document> withTag(UUID tagId) {
        return (root, query, cb) -> {
            Join<Document, Tag> tags = root.join("tags");
            return cb.equal(tags.get("id"), tagId);
        };
    }

    public static Specification<Document> withCorrespondent(UUID correspondentId) {
        return (root, query, cb) -> cb.equal(root.get("correspondent").get("id"), correspondentId);
    }

    public static Specification<Document> withDocumentType(UUID documentTypeId) {
        return (root, query, cb) -> cb.equal(root.get("documentType").get("id"), documentTypeId);
    }

    public static Specification<Document> titleContains(String fragment) {
        return (root, query, cb) ->
                cb.like(cb.lower(root.get("title")), "%" + fragment.toLowerCase() + "%");
    }

    public static Specification<Document> from(String ownerId, DocumentSearchCriteria criteria) {
        Specification<Document> spec = forOwner(ownerId);

        if (criteria.status() != null) {
            spec = spec.and(withStatus(criteria.status()));
        } else {
            spec = spec.and(notPermanentlyDeleted());
        }

        if (criteria.tagId() != null) {
            spec = spec.and(withTag(criteria.tagId()));
        }
        if (criteria.correspondentId() != null) {
            spec = spec.and(withCorrespondent(criteria.correspondentId()));
        }
        if (criteria.documentTypeId() != null) {
            spec = spec.and(withDocumentType(criteria.documentTypeId()));
        }
        if (criteria.titleContains() != null && !criteria.titleContains().isBlank()) {
            spec = spec.and(titleContains(criteria.titleContains()));
        }

        return spec;
    }
}
