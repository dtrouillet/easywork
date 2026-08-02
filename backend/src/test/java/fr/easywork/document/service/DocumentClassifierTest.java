package fr.easywork.document.service;

import fr.easywork.document.domain.Correspondent;
import fr.easywork.document.domain.Document;
import fr.easywork.document.domain.DocumentType;
import fr.easywork.document.domain.Tag;
import fr.easywork.document.repository.CorrespondentRepository;
import fr.easywork.document.repository.DocumentTypeRepository;
import fr.easywork.document.repository.TagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentClassifierTest {

    @Mock CorrespondentRepository correspondentRepository;
    @Mock TagRepository tagRepository;
    @Mock DocumentTypeRepository documentTypeRepository;

    @InjectMocks DocumentClassifier classifier;

    @Test
    void classify_suggestsMatchingCorrespondent() {
        Correspondent edf = new Correspondent("EDF");
        when(correspondentRepository.findAll()).thenReturn(List.of(edf));
        when(tagRepository.findAll()).thenReturn(List.of());
        when(documentTypeRepository.findAll()).thenReturn(List.of());
        Document doc = documentWithText("Facture EDF du mois de juillet");

        ClassificationSuggestionResult result = classifier.classify(doc);

        assertThat(result.correspondent()).isEqualTo(edf);
        assertThat(doc.getCorrespondent()).isNull();
    }

    @Test
    void classify_isCaseInsensitive() {
        Correspondent edf = new Correspondent("EDF");
        when(correspondentRepository.findAll()).thenReturn(List.of(edf));
        when(tagRepository.findAll()).thenReturn(List.of());
        when(documentTypeRepository.findAll()).thenReturn(List.of());
        Document doc = documentWithText("facture edf du mois de juillet");

        ClassificationSuggestionResult result = classifier.classify(doc);

        assertThat(result.correspondent()).isEqualTo(edf);
    }

    @Test
    void classify_suggestsAllMatchingTags() {
        Tag energie = new Tag("energie");
        Tag facture = new Tag("facture");
        when(correspondentRepository.findAll()).thenReturn(List.of());
        when(tagRepository.findAll()).thenReturn(List.of(energie, facture));
        when(documentTypeRepository.findAll()).thenReturn(List.of());
        Document doc = documentWithText("facture energie edf");

        ClassificationSuggestionResult result = classifier.classify(doc);

        assertThat(result.tags()).containsExactlyInAnyOrder(energie, facture);
    }

    @Test
    void classify_suggestsMatchingDocumentType() {
        DocumentType invoice = new DocumentType("Facture");
        when(correspondentRepository.findAll()).thenReturn(List.of());
        when(tagRepository.findAll()).thenReturn(List.of());
        when(documentTypeRepository.findAll()).thenReturn(List.of(invoice));
        Document doc = documentWithText("Facture EDF du mois de juillet");

        ClassificationSuggestionResult result = classifier.classify(doc);

        assertThat(result.documentType()).isEqualTo(invoice);
    }

    @Test
    void classify_suggestsNothing_whenNoMatch() {
        when(correspondentRepository.findAll()).thenReturn(List.of(new Correspondent("Allianz")));
        when(tagRepository.findAll()).thenReturn(List.of(new Tag("sante")));
        when(documentTypeRepository.findAll()).thenReturn(List.of(new DocumentType("Attestation")));
        Document doc = documentWithText("Facture EDF du mois de juillet");

        ClassificationSuggestionResult result = classifier.classify(doc);

        assertThat(result.correspondent()).isNull();
        assertThat(result.documentType()).isNull();
        assertThat(result.tags()).isEmpty();
    }

    @Test
    void classify_doesNotSuggestCorrespondent_whenAlreadySetManually() {
        Correspondent manual = new Correspondent("Allianz");
        when(tagRepository.findAll()).thenReturn(List.of());
        when(documentTypeRepository.findAll()).thenReturn(List.of());
        Document doc = documentWithText("Facture EDF du mois de juillet");
        doc.setCorrespondent(manual);

        ClassificationSuggestionResult result = classifier.classify(doc);

        assertThat(result.correspondent()).isNull();
        verify(correspondentRepository, never()).findAll();
    }

    @Test
    void classify_doesNotSuggestTags_whenAlreadySetManually() {
        Tag manual = new Tag("perso");
        when(correspondentRepository.findAll()).thenReturn(List.of());
        when(documentTypeRepository.findAll()).thenReturn(List.of());
        Document doc = documentWithText("facture energie edf");
        doc.setTags(new HashSet<>(Set.of(manual)));

        ClassificationSuggestionResult result = classifier.classify(doc);

        assertThat(result.tags()).isEmpty();
        verify(tagRepository, never()).findAll();
    }

    @Test
    void classify_returnsEmptyResult_whenExtractedTextIsBlank() {
        Document doc = documentWithText("   ");

        ClassificationSuggestionResult result = classifier.classify(doc);

        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    void classify_returnsEmptyResult_whenExtractedTextIsNull() {
        Document doc = documentWithText(null);

        ClassificationSuggestionResult result = classifier.classify(doc);

        assertThat(result.isEmpty()).isTrue();
    }

    private static Document documentWithText(String text) {
        Document doc = new Document();
        doc.setExtractedText(text);
        return doc;
    }
}
