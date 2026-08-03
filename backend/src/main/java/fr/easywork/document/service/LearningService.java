package fr.easywork.document.service;

import fr.easywork.document.domain.ClassificationSignalTagAssociation;
import fr.easywork.document.domain.ClassificationSignalType;
import fr.easywork.document.domain.Document;
import fr.easywork.document.domain.Tag;
import fr.easywork.document.repository.ClassificationSignalTagAssociationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/**
 * Progressive learning (ADR 0003): every manual save that sets both a
 * correspondent (and/or document type) and tags increments a
 * signal -> tag association, so the same tags get suggested on similar
 * documents afterwards — no separate training job.
 */
@Component
@RequiredArgsConstructor
class LearningService {

    private final ClassificationSignalTagAssociationRepository associationRepository;

    void recordConfirmation(Document doc) {
        if (doc.getTags().isEmpty()) {
            return;
        }
        if (doc.getCorrespondent() != null) {
            recordSignal(ClassificationSignalType.CORRESPONDENT, doc.getCorrespondent().getId(), doc.getTags());
        }
        if (doc.getDocumentType() != null) {
            recordSignal(ClassificationSignalType.DOCUMENT_TYPE, doc.getDocumentType().getId(), doc.getTags());
        }
    }

    private void recordSignal(ClassificationSignalType signalType, UUID signalId, Set<Tag> tags) {
        for (Tag tag : tags) {
            ClassificationSignalTagAssociation association = associationRepository
                .findBySignalTypeAndSignalIdAndTagId(signalType, signalId, tag.getId())
                .orElseGet(() -> new ClassificationSignalTagAssociation(signalType, signalId, tag));
            association.recordConfirmation();
            associationRepository.save(association);
        }
    }
}
