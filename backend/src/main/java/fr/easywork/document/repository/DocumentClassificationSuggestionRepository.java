package fr.easywork.document.repository;

import fr.easywork.document.domain.DocumentClassificationSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DocumentClassificationSuggestionRepository
        extends JpaRepository<DocumentClassificationSuggestion, UUID> {
}
