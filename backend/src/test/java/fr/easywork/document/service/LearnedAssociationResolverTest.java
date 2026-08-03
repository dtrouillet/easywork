package fr.easywork.document.service;

import fr.easywork.document.domain.ClassificationSignalTagAssociation;
import fr.easywork.document.domain.ClassificationSignalType;
import fr.easywork.document.domain.Correspondent;
import fr.easywork.document.domain.DocumentType;
import fr.easywork.document.domain.Tag;
import fr.easywork.document.repository.ClassificationSignalTagAssociationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearnedAssociationResolverTest {

    @Mock ClassificationSignalTagAssociationRepository associationRepository;

    @InjectMocks LearnedAssociationResolver resolver;

    @Test
    void resolve_returnsTagsLearnedForCorrespondent() {
        UUID correspondentId = UUID.randomUUID();
        Correspondent correspondent = new Correspondent("EDF");
        correspondent.setId(correspondentId);
        Tag energie = new Tag("energie");

        var association = new ClassificationSignalTagAssociation(ClassificationSignalType.CORRESPONDENT, correspondentId, energie);
        association.recordConfirmation();
        when(associationRepository.findBySignalTypeAndSignalId(ClassificationSignalType.CORRESPONDENT, correspondentId))
            .thenReturn(List.of(association));

        Set<Tag> result = resolver.resolve(correspondent, null);

        assertThat(result).containsExactly(energie);
    }

    @Test
    void resolve_combinesCorrespondentAndDocumentTypeSignals() {
        UUID correspondentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        Correspondent correspondent = new Correspondent("EDF");
        correspondent.setId(correspondentId);
        DocumentType type = new DocumentType("Facture");
        type.setId(typeId);
        Tag energie = new Tag("energie");
        Tag facture = new Tag("facture");

        var correspondentAssociation =
            new ClassificationSignalTagAssociation(ClassificationSignalType.CORRESPONDENT, correspondentId, energie);
        correspondentAssociation.recordConfirmation();
        var typeAssociation =
            new ClassificationSignalTagAssociation(ClassificationSignalType.DOCUMENT_TYPE, typeId, facture);
        typeAssociation.recordConfirmation();
        when(associationRepository.findBySignalTypeAndSignalId(ClassificationSignalType.CORRESPONDENT, correspondentId))
            .thenReturn(List.of(correspondentAssociation));
        when(associationRepository.findBySignalTypeAndSignalId(ClassificationSignalType.DOCUMENT_TYPE, typeId))
            .thenReturn(List.of(typeAssociation));

        Set<Tag> result = resolver.resolve(correspondent, type);

        assertThat(result).containsExactlyInAnyOrder(energie, facture);
    }

    @Test
    void resolve_returnsEmpty_whenNoCorrespondentOrType() {
        Set<Tag> result = resolver.resolve(null, null);

        assertThat(result).isEmpty();
    }
}
