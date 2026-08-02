package fr.easywork.document.service;

import fr.easywork.document.domain.ClassificationSignalType;
import fr.easywork.document.domain.Correspondent;
import fr.easywork.document.domain.DocumentType;
import fr.easywork.document.domain.Tag;
import fr.easywork.document.repository.ClassificationSignalTagAssociationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Reads back the associations {@code LearningService} recorded from prior
 * confirmed classifications, to suggest tags a user has already confirmed for
 * documents sharing the same correspondent/document type (ADR 0003).
 */
@Component
@RequiredArgsConstructor
class LearnedAssociationResolver {

    private final ClassificationSignalTagAssociationRepository associationRepository;

    Set<Tag> resolve(Correspondent correspondent, DocumentType documentType) {
        Set<Tag> tags = new HashSet<>();
        if (correspondent != null) {
            addTags(tags, ClassificationSignalType.CORRESPONDENT, correspondent.getId());
        }
        if (documentType != null) {
            addTags(tags, ClassificationSignalType.DOCUMENT_TYPE, documentType.getId());
        }
        return tags;
    }

    private void addTags(Set<Tag> tags, ClassificationSignalType signalType, UUID signalId) {
        associationRepository.findBySignalTypeAndSignalId(signalType, signalId).stream()
            .filter(association -> association.getConfirmationCount() >= 1)
            .map(association -> association.getTag())
            .forEach(tags::add);
    }
}
