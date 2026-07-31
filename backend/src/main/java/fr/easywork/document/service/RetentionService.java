package fr.easywork.document.service;

import fr.easywork.document.domain.Document;
import fr.easywork.document.domain.DocumentStatus;
import fr.easywork.document.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class RetentionService {

    private static final Logger log = LoggerFactory.getLogger(RetentionService.class);
    private static final int BATCH_SIZE = 100;

    private final DocumentRepository documentRepository;

    /**
     * Runs daily at 02:00 UTC. Moves READY documents past their retention period to TRASH.
     * Processes in pages of {@value BATCH_SIZE} to stay bounded on large datasets.
     * Idempotent: documents already in TRASH or beyond are unaffected.
     */
    @Scheduled(cron = "0 0 2 * * *", zone = "UTC")
    public void applyRetentionPolicies() {
        Instant now = Instant.now();
        int totalTrashed = 0;
        int pageNumber = 0;

        Page<Document> page;
        do {
            page = documentRepository.findExpiredDocuments(now, PageRequest.of(pageNumber, BATCH_SIZE));
            totalTrashed += trashPage(page, now);
            pageNumber++;
        } while (page.hasNext());

        if (totalTrashed > 0) {
            log.info("Retention policy run complete: {} document(s) moved to TRASH", totalTrashed);
        }
    }

    @Transactional
    int trashPage(Page<Document> page, Instant now) {
        int count = 0;
        for (Document doc : page.getContent()) {
            try {
                doc.transitionTo(DocumentStatus.TRASH);
                doc.setTrashedAt(now);
                documentRepository.save(doc);
                count++;
            } catch (IllegalStateException e) {
                // Document was already moved out of READY by a concurrent operation — skip
                log.warn("Retention: skipped document {} — unexpected status {}", doc.getId(), doc.getStatus());
            }
        }
        return count;
    }
}
