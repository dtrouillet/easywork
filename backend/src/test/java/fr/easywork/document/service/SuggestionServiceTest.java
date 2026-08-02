package fr.easywork.document.service;

import fr.easywork.document.domain.ClassificationSignalTagAssociation;
import fr.easywork.document.domain.ClassificationSignalType;
import fr.easywork.document.domain.Correspondent;
import fr.easywork.document.domain.Document;
import fr.easywork.document.domain.DocumentClassificationSuggestion;
import fr.easywork.document.domain.DocumentExtractedEntity;
import fr.easywork.document.domain.DocumentStatus;
import fr.easywork.document.domain.DocumentType;
import fr.easywork.document.domain.SuggestionSource;
import fr.easywork.document.domain.SuggestionStatus;
import fr.easywork.document.domain.Tag;
import fr.easywork.document.dto.ConfirmSuggestionRequest;
import fr.easywork.document.dto.DocumentClassificationSuggestionDto;
import fr.easywork.document.event.DocumentReadyEvent;
import fr.easywork.document.event.ExtractedEntityPayload;
import fr.easywork.document.event.ExtractedEntityType;
import fr.easywork.document.exception.DocumentNotFoundException;
import fr.easywork.document.exception.SuggestionNotFoundException;
import fr.easywork.document.mapper.DocumentMapper;
import fr.easywork.document.repository.ClassificationSignalTagAssociationRepository;
import fr.easywork.document.repository.CorrespondentRepository;
import fr.easywork.document.repository.DocumentClassificationSuggestionRepository;
import fr.easywork.document.repository.DocumentExtractedEntityRepository;
import fr.easywork.document.repository.DocumentRepository;
import fr.easywork.document.repository.DocumentTypeRepository;
import fr.easywork.document.repository.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuggestionServiceTest {

    @Mock CorrespondentRepository correspondentRepository;
    @Mock TagRepository tagRepository;
    @Mock DocumentTypeRepository documentTypeRepository;
    @Mock ClassificationSignalTagAssociationRepository associationRepository;
    @Mock DocumentRepository documentRepository;
    @Mock DocumentExtractedEntityRepository extractedEntityRepository;
    @Mock DocumentClassificationSuggestionRepository suggestionRepository;
    @Mock DocumentMapper mapper;
    @Mock ApplicationEventPublisher eventPublisher;

    private SuggestionService suggestionService;

    @BeforeEach
    void setUp() {
        var documentClassifier = new DocumentClassifier(correspondentRepository, tagRepository, documentTypeRepository);
        var learnedAssociationResolver = new LearnedAssociationResolver(associationRepository);
        var learningService = new LearningService(associationRepository);
        suggestionService = new SuggestionService(
            documentClassifier, learnedAssociationResolver, learningService,
            documentRepository, extractedEntityRepository, suggestionRepository, mapper, eventPublisher);
    }

    private static Document docWithId(UUID id, String text) {
        Document doc = new Document();
        doc.setId(id);
        doc.setOwnerId("user1");
        doc.setExtractedText(text);
        return doc;
    }

    // --- generateSuggestion ---

    @Test
    void generateSuggestion_persistsExtractedEntities() {
        UUID id = UUID.randomUUID();
        Document doc = docWithId(id, "Facture EDF");
        when(correspondentRepository.findAll()).thenReturn(List.of());
        when(tagRepository.findAll()).thenReturn(List.of());
        when(documentTypeRepository.findAll()).thenReturn(List.of());
        when(suggestionRepository.findById(id)).thenReturn(Optional.empty());
        when(suggestionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var payloads = List.of(new ExtractedEntityPayload(ExtractedEntityType.AMOUNT, "12€", "12"));
        suggestionService.generateSuggestion(doc, payloads);

        var captor = ArgumentCaptor.forClass(List.class);
        verify(extractedEntityRepository).saveAll(captor.capture());
        List<DocumentExtractedEntity> saved = captor.getValue();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getRawValue()).isEqualTo("12€");
    }

    @Test
    void generateSuggestion_createsNewSuggestion_withHeuristicMatches() {
        UUID id = UUID.randomUUID();
        Document doc = docWithId(id, "Facture EDF du mois");
        Correspondent edf = new Correspondent("EDF");
        when(correspondentRepository.findAll()).thenReturn(List.of(edf));
        when(tagRepository.findAll()).thenReturn(List.of());
        when(documentTypeRepository.findAll()).thenReturn(List.of());
        when(associationRepository.findBySignalTypeAndSignalId(any(), any())).thenReturn(List.of());
        when(suggestionRepository.findById(id)).thenReturn(Optional.empty());
        when(suggestionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        suggestionService.generateSuggestion(doc, List.of());

        var captor = ArgumentCaptor.forClass(DocumentClassificationSuggestion.class);
        verify(suggestionRepository).save(captor.capture());
        assertThat(captor.getValue().getSuggestedCorrespondent()).isEqualTo(edf);
        assertThat(captor.getValue().getSource()).isEqualTo(SuggestionSource.HEURISTIC);
        assertThat(captor.getValue().getStatus()).isEqualTo(SuggestionStatus.PENDING);
    }

    @Test
    void generateSuggestion_includesLearnedTags_whenCorrespondentMatched() {
        UUID id = UUID.randomUUID();
        Document doc = docWithId(id, "Facture EDF du mois");
        Correspondent edf = new Correspondent("EDF");
        UUID edfId = UUID.randomUUID();
        edf.setId(edfId);
        Tag energie = new Tag("energie");
        when(correspondentRepository.findAll()).thenReturn(List.of(edf));
        when(tagRepository.findAll()).thenReturn(List.of());
        when(documentTypeRepository.findAll()).thenReturn(List.of());
        var learned = new ClassificationSignalTagAssociation(ClassificationSignalType.CORRESPONDENT, edfId, energie);
        learned.recordConfirmation();
        when(associationRepository.findBySignalTypeAndSignalId(ClassificationSignalType.CORRESPONDENT, edfId))
            .thenReturn(List.of(learned));
        when(suggestionRepository.findById(id)).thenReturn(Optional.empty());
        when(suggestionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        suggestionService.generateSuggestion(doc, List.of());

        var captor = ArgumentCaptor.forClass(DocumentClassificationSuggestion.class);
        verify(suggestionRepository).save(captor.capture());
        assertThat(captor.getValue().getSuggestedTags()).containsExactly(energie);
        assertThat(captor.getValue().getSource()).isEqualTo(SuggestionSource.LEARNED);
    }

    @Test
    void generateSuggestion_regeneratesExistingSuggestion_insteadOfCreatingSecondRow() {
        UUID id = UUID.randomUUID();
        Document doc = docWithId(id, "");
        var existing = new DocumentClassificationSuggestion(id, SuggestionSource.HEURISTIC);
        existing.confirm();
        when(suggestionRepository.findById(id)).thenReturn(Optional.of(existing));
        when(suggestionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        suggestionService.generateSuggestion(doc, List.of());

        var captor = ArgumentCaptor.forClass(DocumentClassificationSuggestion.class);
        verify(suggestionRepository).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(existing);
        assertThat(captor.getValue().getStatus()).isEqualTo(SuggestionStatus.PENDING);
        verify(correspondentRepository, never()).findAll(); // blank text short-circuits DocumentClassifier
    }

    // --- getSuggestion ---

    @Test
    void getSuggestion_returnsMappedDto_whenFound() {
        UUID id = UUID.randomUUID();
        Document doc = docWithId(id, null);
        var suggestion = new DocumentClassificationSuggestion(id, SuggestionSource.HEURISTIC);
        var dto = new DocumentClassificationSuggestionDto(id, null, null, null, List.of(), null, null, null, null, null);
        when(documentRepository.findByIdAndOwnerId(id, "user1")).thenReturn(Optional.of(doc));
        when(suggestionRepository.findById(id)).thenReturn(Optional.of(suggestion));
        when(mapper.toDto(suggestion)).thenReturn(dto);

        var result = suggestionService.getSuggestion(id, "user1");

        assertThat(result).isSameAs(dto);
    }

    @Test
    void getSuggestion_throwsNotFound_whenDocumentMissing() {
        UUID id = UUID.randomUUID();
        when(documentRepository.findByIdAndOwnerId(id, "user1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> suggestionService.getSuggestion(id, "user1"))
            .isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void getSuggestion_throwsSuggestionNotFound_whenNoSuggestionYet() {
        UUID id = UUID.randomUUID();
        Document doc = docWithId(id, null);
        when(documentRepository.findByIdAndOwnerId(id, "user1")).thenReturn(Optional.of(doc));
        when(suggestionRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> suggestionService.getSuggestion(id, "user1"))
            .isInstanceOf(SuggestionNotFoundException.class);
    }

    // --- confirmSuggestion ---

    @Test
    void confirmSuggestion_appliesOnlyAcceptedFields_andRecordsLearning() {
        UUID id = UUID.randomUUID();
        Document doc = docWithId(id, null);
        doc.setStatus(DocumentStatus.READY);
        Correspondent edf = new Correspondent("EDF");
        DocumentType invoice = new DocumentType("Facture");
        Tag energie = new Tag("energie");
        energie.setId(UUID.randomUUID());

        var suggestion = new DocumentClassificationSuggestion(id, SuggestionSource.HEURISTIC);
        suggestion.setSuggestedCorrespondent(edf);
        suggestion.setSuggestedDocumentType(invoice);
        suggestion.setSuggestedTags(new HashSet<>(Set.of(energie)));

        when(documentRepository.findByIdAndOwnerId(id, "user1")).thenReturn(Optional.of(doc));
        when(suggestionRepository.findById(id)).thenReturn(Optional.of(suggestion));
        when(suggestionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var request = new ConfirmSuggestionRequest(true, false, false, Set.of(energie.getId()));
        suggestionService.confirmSuggestion(id, "user1", request);

        assertThat(doc.getCorrespondent()).isEqualTo(edf);
        assertThat(doc.getDocumentType()).isNull(); // acceptDocumentType was false
        assertThat(doc.getTags()).containsExactly(energie);
        assertThat(suggestion.getStatus()).isEqualTo(SuggestionStatus.CONFIRMED);
        verify(documentRepository).save(doc);
        verify(eventPublisher).publishEvent(any(DocumentReadyEvent.class));
    }

    @Test
    void confirmSuggestion_doesNotPublishReindex_whenDocumentNotSearchable() {
        UUID id = UUID.randomUUID();
        Document doc = docWithId(id, null);
        doc.setStatus(DocumentStatus.RECEIVED);
        var suggestion = new DocumentClassificationSuggestion(id, SuggestionSource.HEURISTIC);
        when(documentRepository.findByIdAndOwnerId(id, "user1")).thenReturn(Optional.of(doc));
        when(suggestionRepository.findById(id)).thenReturn(Optional.of(suggestion));
        when(suggestionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        suggestionService.confirmSuggestion(id, "user1", new ConfirmSuggestionRequest(false, false, false, Set.of()));

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void confirmSuggestion_throwsSuggestionNotFound_whenMissing() {
        UUID id = UUID.randomUUID();
        Document doc = docWithId(id, null);
        when(documentRepository.findByIdAndOwnerId(id, "user1")).thenReturn(Optional.of(doc));
        when(suggestionRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            suggestionService.confirmSuggestion(id, "user1", new ConfirmSuggestionRequest(true, true, true, Set.of())))
            .isInstanceOf(SuggestionNotFoundException.class);
    }

    // --- rejectSuggestion ---

    @Test
    void rejectSuggestion_marksRejected_withoutTouchingDocument() {
        UUID id = UUID.randomUUID();
        Document doc = docWithId(id, null);
        var suggestion = new DocumentClassificationSuggestion(id, SuggestionSource.HEURISTIC);
        suggestion.setSuggestedCorrespondent(new Correspondent("EDF"));
        when(documentRepository.findByIdAndOwnerId(id, "user1")).thenReturn(Optional.of(doc));
        when(suggestionRepository.findById(id)).thenReturn(Optional.of(suggestion));
        when(suggestionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        suggestionService.rejectSuggestion(id, "user1");

        assertThat(suggestion.getStatus()).isEqualTo(SuggestionStatus.REJECTED);
        assertThat(doc.getCorrespondent()).isNull();
        verify(documentRepository, never()).save(any());
    }
}
