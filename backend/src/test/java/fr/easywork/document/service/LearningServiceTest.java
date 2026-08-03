package fr.easywork.document.service;

import fr.easywork.document.domain.ClassificationSignalTagAssociation;
import fr.easywork.document.domain.ClassificationSignalType;
import fr.easywork.document.domain.Correspondent;
import fr.easywork.document.domain.Document;
import fr.easywork.document.domain.DocumentType;
import fr.easywork.document.domain.Tag;
import fr.easywork.document.repository.ClassificationSignalTagAssociationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningServiceTest {

    @Mock ClassificationSignalTagAssociationRepository associationRepository;

    @InjectMocks LearningService learningService;

    @Test
    void recordConfirmation_createsNewAssociation_whenNoneExistsYet() {
        UUID correspondentId = UUID.randomUUID();
        Correspondent correspondent = new Correspondent("EDF");
        correspondent.setId(correspondentId);
        Tag tag = new Tag("energie");
        tag.setId(UUID.randomUUID());
        Document doc = new Document();
        doc.setCorrespondent(correspondent);
        doc.setTags(new HashSet<>(Set.of(tag)));

        when(associationRepository.findBySignalTypeAndSignalIdAndTagId(
            ClassificationSignalType.CORRESPONDENT, correspondentId, tag.getId()))
            .thenReturn(Optional.empty());

        learningService.recordConfirmation(doc);

        var captor = ArgumentCaptor.forClass(ClassificationSignalTagAssociation.class);
        verify(associationRepository).save(captor.capture());
        assertThat(captor.getValue().getSignalType()).isEqualTo(ClassificationSignalType.CORRESPONDENT);
        assertThat(captor.getValue().getSignalId()).isEqualTo(correspondentId);
        assertThat(captor.getValue().getTag()).isEqualTo(tag);
        assertThat(captor.getValue().getConfirmationCount()).isEqualTo(1);
    }

    @Test
    void recordConfirmation_incrementsExistingAssociation() {
        UUID correspondentId = UUID.randomUUID();
        Correspondent correspondent = new Correspondent("EDF");
        correspondent.setId(correspondentId);
        Tag tag = new Tag("energie");
        tag.setId(UUID.randomUUID());
        Document doc = new Document();
        doc.setCorrespondent(correspondent);
        doc.setTags(new HashSet<>(Set.of(tag)));

        var existing = new ClassificationSignalTagAssociation(ClassificationSignalType.CORRESPONDENT, correspondentId, tag);
        when(associationRepository.findBySignalTypeAndSignalIdAndTagId(
            ClassificationSignalType.CORRESPONDENT, correspondentId, tag.getId()))
            .thenReturn(Optional.of(existing));

        learningService.recordConfirmation(doc);
        learningService.recordConfirmation(doc);

        assertThat(existing.getConfirmationCount()).isEqualTo(2);
    }

    @Test
    void recordConfirmation_recordsBothCorrespondentAndDocumentTypeSignals() {
        UUID correspondentId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        Correspondent correspondent = new Correspondent("EDF");
        correspondent.setId(correspondentId);
        DocumentType type = new DocumentType("Facture");
        type.setId(typeId);
        Tag tag = new Tag("energie");
        tag.setId(UUID.randomUUID());
        Document doc = new Document();
        doc.setCorrespondent(correspondent);
        doc.setDocumentType(type);
        doc.setTags(new HashSet<>(Set.of(tag)));

        when(associationRepository.findBySignalTypeAndSignalIdAndTagId(any(), any(), any()))
            .thenReturn(Optional.empty());

        learningService.recordConfirmation(doc);

        verify(associationRepository, times(2)).save(any());
    }

    @Test
    void recordConfirmation_doesNothing_whenNoTags() {
        Document doc = new Document();
        doc.setCorrespondent(new Correspondent("EDF"));

        learningService.recordConfirmation(doc);

        verify(associationRepository, never()).save(any());
    }

    @Test
    void recordConfirmation_doesNothing_whenNoCorrespondentOrType() {
        Tag tag = new Tag("energie");
        Document doc = new Document();
        doc.setTags(new HashSet<>(Set.of(tag)));

        learningService.recordConfirmation(doc);

        verify(associationRepository, never()).save(any());
    }
}
