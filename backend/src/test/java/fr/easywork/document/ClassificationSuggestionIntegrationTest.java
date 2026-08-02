package fr.easywork.document;

import fr.easywork.AbstractIntegrationTest;
import fr.easywork.document.domain.ClassificationSignalType;
import fr.easywork.document.domain.Correspondent;
import fr.easywork.document.domain.Document;
import fr.easywork.document.domain.DocumentStatus;
import fr.easywork.document.domain.SuggestionStatus;
import fr.easywork.document.domain.Tag;
import fr.easywork.document.dto.ConfirmSuggestionRequest;
import fr.easywork.document.dto.DocumentClassificationSuggestionDto;
import fr.easywork.document.event.ExtractedEntityPayload;
import fr.easywork.document.event.ExtractedEntityType;
import fr.easywork.document.event.IngestCompletedEvent;
import fr.easywork.document.repository.ClassificationSignalTagAssociationRepository;
import fr.easywork.document.repository.CorrespondentRepository;
import fr.easywork.document.repository.DocumentClassificationSuggestionRepository;
import fr.easywork.document.repository.DocumentExtractedEntityRepository;
import fr.easywork.document.repository.DocumentRepository;
import fr.easywork.document.repository.TagRepository;
import fr.easywork.document.service.DocumentService;
import fr.easywork.document.service.SuggestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-cycle integration test for ADR 0003: real PostgreSQL (Testcontainers).
 * Covers the ingest -> suggestion round trip, confirm/reject, learned
 * associations, and GDPR erasure cascade for the three new tables.
 *
 * {@code onIngestCompleted} is {@code @ApplicationModuleListener}, which Spring
 * Modulith's completion-tracking proxy runs asynchronously even when invoked
 * directly on the (proxied) autowired bean — so every call below is followed
 * by {@link #awaitProcessed} rather than asserting immediately afterward.
 */
class ClassificationSuggestionIntegrationTest extends AbstractIntegrationTest {

    @Autowired DocumentService documentService;
    @Autowired SuggestionService suggestionService;
    @Autowired DocumentRepository documentRepository;
    @Autowired DocumentExtractedEntityRepository extractedEntityRepository;
    @Autowired DocumentClassificationSuggestionRepository suggestionRepository;
    @Autowired ClassificationSignalTagAssociationRepository associationRepository;
    @Autowired CorrespondentRepository correspondentRepository;
    @Autowired TagRepository tagRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    private static final String OWNER = "suggestion-integration-user";

    @Test
    @WithMockUser
    void onIngestCompleted_persistsExtractedEntities_andGeneratesHeuristicSuggestion() {
        Correspondent edf = correspondentRepository.save(new Correspondent("EDF Ingest"));
        Document doc = receivedDoc("Facture EDF Ingest");
        documentRepository.save(doc);

        var event = new IngestCompletedEvent(
            doc.getId(), "hash-1", "Facture EDF Ingest du mois de mars, montant 42,00 €", 1, false, true, null,
            List.of(new ExtractedEntityPayload(ExtractedEntityType.AMOUNT, "42,00 €", "42.00")));

        documentService.onIngestCompleted(event);
        awaitProcessed(doc.getId());

        Document ready = documentRepository.findById(doc.getId()).orElseThrow();
        assertThat(ready.getStatus()).isEqualTo(DocumentStatus.READY);
        // The old silent classifier is gone: the correspondent is NOT applied directly.
        assertThat(ready.getCorrespondent()).isNull();

        assertThat(extractedEntityRepository.findByDocumentId(doc.getId())).hasSize(1);

        // Read back through the service (DTO mapped inside its own transaction) rather than
        // the raw entity — its correspondent/tags are lazy associations that would otherwise
        // throw LazyInitializationException once accessed outside a Hibernate session.
        DocumentClassificationSuggestionDto suggestion = suggestionService.getSuggestion(doc.getId(), OWNER);
        assertThat(suggestion.suggestedCorrespondent().id()).isEqualTo(edf.getId());
        assertThat(suggestion.status()).isEqualTo(SuggestionStatus.PENDING);
    }

    @Test
    @WithMockUser
    void confirmSuggestion_appliesFields_andRecordsLearnedAssociation() {
        Correspondent edf = correspondentRepository.save(new Correspondent("EDF Confirm"));
        Tag energie = tagRepository.save(new Tag("energie-confirm"));
        Document doc = receivedDoc("Facture EDF Confirm");
        documentRepository.save(doc);

        documentService.onIngestCompleted(new IngestCompletedEvent(
            doc.getId(), "hash-2", "Facture EDF Confirm energie-confirm", 1, false, true, null, List.of()));
        awaitProcessed(doc.getId());

        var request = new ConfirmSuggestionRequest(true, false, false, Set.of(energie.getId()));
        DocumentClassificationSuggestionDto confirmed =
            suggestionService.confirmSuggestion(doc.getId(), OWNER, request);

        assertThat(confirmed.status()).isEqualTo(SuggestionStatus.CONFIRMED);

        var updated = documentService.get(doc.getId(), OWNER);
        assertThat(updated.correspondent().id()).isEqualTo(edf.getId());
        assertThat(updated.tags()).extracting(t -> t.name()).containsExactly("energie-confirm");

        // Learning: correspondent EDF Confirm -> tag "energie-confirm" should now be recorded.
        // (.getTag().getId() only, not .getName() — the association's tag is a lazy proxy that
        // would throw LazyInitializationException if resolved outside this repository call's session.)
        assertThat(associationRepository.findBySignalTypeAndSignalId(ClassificationSignalType.CORRESPONDENT, edf.getId()))
            .extracting(a -> a.getTag().getId())
            .containsExactly(energie.getId());
    }

    @Test
    @WithMockUser
    void rejectSuggestion_leavesDocumentUnclassified() {
        correspondentRepository.save(new Correspondent("EDF Reject"));
        Document doc = receivedDoc("Facture EDF Reject");
        documentRepository.save(doc);

        documentService.onIngestCompleted(new IngestCompletedEvent(
            doc.getId(), "hash-3", "Facture EDF Reject", 1, false, true, null, List.of()));
        awaitProcessed(doc.getId());

        suggestionService.rejectSuggestion(doc.getId(), OWNER);

        Document unchanged = documentRepository.findById(doc.getId()).orElseThrow();
        assertThat(unchanged.getCorrespondent()).isNull();
        assertThat(suggestionRepository.findById(doc.getId()).orElseThrow().getStatus())
            .isEqualTo(SuggestionStatus.REJECTED);
    }

    @Test
    @WithMockUser
    void reclassify_regeneratesSuggestion_afterNewTagIsCreated() {
        Document doc = receivedDoc("Facture Orange telephonereclassify");
        documentRepository.save(doc);
        documentService.onIngestCompleted(new IngestCompletedEvent(
            doc.getId(), "hash-4", "Facture Orange telephonereclassify", 1, false, true, null, List.of()));
        awaitProcessed(doc.getId());
        assertThat(suggestionService.getSuggestion(doc.getId(), OWNER).suggestedTags()).isEmpty();

        // A tag matching the text didn't exist at ingest time; create it now and reclassify.
        Tag telecom = tagRepository.save(new Tag("telephonereclassify"));

        DocumentClassificationSuggestionDto regenerated = documentService.reclassify(doc.getId(), OWNER);

        assertThat(regenerated.suggestedTags()).extracting(t -> t.id()).containsExactly(telecom.getId());
        assertThat(suggestionRepository.findAllById(List.of(doc.getId()))).hasSize(1); // regenerated in place, not duplicated
    }

    @Test
    @WithMockUser
    void permanentDelete_cascadesExtractedEntitiesAndSuggestion() {
        Document doc = receivedDoc("Facture EDF");
        documentRepository.save(doc);
        documentService.onIngestCompleted(new IngestCompletedEvent(
            doc.getId(), "hash-5", "Facture EDF", 1, false, true, null,
            List.of(new ExtractedEntityPayload(ExtractedEntityType.AMOUNT, "10€", "10"))));
        UUID docId = doc.getId();
        awaitProcessed(docId);

        assertThat(extractedEntityRepository.findByDocumentId(docId)).isNotEmpty();
        assertThat(suggestionRepository.findById(docId)).isPresent();

        documentService.trash(docId, OWNER);
        documentService.permanentDelete(docId, OWNER);

        assertThat(documentRepository.findById(docId)).isEmpty();
        assertThat(extractedEntityRepository.findByDocumentId(docId)).isEmpty();
        assertThat(suggestionRepository.findById(docId)).isEmpty();

        List<Map<String, Object>> orphanedSuggestionTags = jdbcTemplate.queryForList(
            "SELECT * FROM document_classification_suggestion_tag WHERE document_id = ?", docId);
        assertThat(orphanedSuggestionTags).isEmpty();
    }

    /** Polls until the async {@code onIngestCompleted} listener has finished processing this document. */
    private void awaitProcessed(UUID documentId) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(deadline)) {
            DocumentStatus status = documentRepository.findById(documentId).orElseThrow().getStatus();
            if (status == DocumentStatus.READY || status == DocumentStatus.FAILED) {
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("Document " + documentId + " did not finish processing within 10s");
    }

    private Document receivedDoc(String title) {
        Document doc = new Document();
        doc.setTitle(title);
        doc.setOriginalFilename(title.replaceAll(" ", "_") + ".pdf");
        doc.setMimeType("application/pdf");
        doc.setOwnerId(OWNER);
        doc.setStatus(DocumentStatus.RECEIVED);
        return doc;
    }
}
