package fr.easywork.document.repository;

import fr.easywork.document.domain.Correspondent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CorrespondentRepository extends JpaRepository<Correspondent, UUID> {
    Optional<Correspondent> findByName(String name);
}
