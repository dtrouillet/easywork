package fr.easywork.document.repository;

import fr.easywork.document.domain.ClassificationSignalTagAssociation;
import fr.easywork.document.domain.ClassificationSignalType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClassificationSignalTagAssociationRepository
        extends JpaRepository<ClassificationSignalTagAssociation, UUID> {

    Optional<ClassificationSignalTagAssociation> findBySignalTypeAndSignalIdAndTagId(
        ClassificationSignalType signalType, UUID signalId, UUID tagId);

    List<ClassificationSignalTagAssociation> findBySignalTypeAndSignalId(
        ClassificationSignalType signalType, UUID signalId);
}
