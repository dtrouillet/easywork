package fr.easywork.document.service;

import fr.easywork.document.domain.Correspondent;
import fr.easywork.document.domain.Document;
import fr.easywork.document.domain.DocumentStatus;
import fr.easywork.document.dto.CorrespondentDto;
import fr.easywork.document.event.DocumentReadyEvent;
import fr.easywork.document.exception.DuplicateNameException;
import fr.easywork.document.exception.EntityInUseException;
import fr.easywork.document.mapper.DocumentMapper;
import fr.easywork.document.repository.CorrespondentRepository;
import fr.easywork.document.repository.DocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CorrespondentServiceTest {

    @Mock CorrespondentRepository correspondentRepository;
    @Mock DocumentRepository documentRepository;
    @Mock DocumentMapper mapper;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks CorrespondentService correspondentService;

    @Test
    void create_savesCorrespondent_whenNameIsNew() {
        when(correspondentRepository.findByName("EDF")).thenReturn(Optional.empty());
        Correspondent saved = new Correspondent("EDF");
        when(correspondentRepository.save(any())).thenReturn(saved);
        when(mapper.toDto(saved)).thenReturn(new CorrespondentDto(UUID.randomUUID(), "EDF"));

        correspondentService.create("EDF");

        verify(correspondentRepository).save(any(Correspondent.class));
    }

    @Test
    void create_throwsDuplicateNameException_whenNameExists() {
        when(correspondentRepository.findByName("EDF")).thenReturn(Optional.of(new Correspondent("EDF")));

        assertThatThrownBy(() -> correspondentService.create("EDF"))
            .isInstanceOf(DuplicateNameException.class);

        verify(correspondentRepository, never()).save(any());
    }

    @Test
    void delete_deletesCorrespondent_whenNotInUse() {
        UUID id = UUID.randomUUID();
        when(documentRepository.existsByCorrespondentId(id)).thenReturn(false);

        correspondentService.delete(id);

        verify(correspondentRepository).deleteById(id);
    }

    @Test
    void delete_throwsEntityInUseException_whenReferencedByDocument() {
        UUID id = UUID.randomUUID();
        when(documentRepository.existsByCorrespondentId(id)).thenReturn(true);

        assertThatThrownBy(() -> correspondentService.delete(id))
            .isInstanceOf(EntityInUseException.class);

        verify(correspondentRepository, never()).deleteById(any());
    }

    // --- update ---

    @Test
    void update_renamesCorrespondent_whenNameIsFree() {
        UUID id = UUID.randomUUID();
        Correspondent correspondent = new Correspondent("old");
        when(correspondentRepository.findById(id)).thenReturn(Optional.of(correspondent));
        when(correspondentRepository.findByName("new")).thenReturn(Optional.empty());
        when(correspondentRepository.save(correspondent)).thenReturn(correspondent);

        correspondentService.update(id, "new");

        assertThat(correspondent.getName()).isEqualTo("new");
    }

    @Test
    void update_throwsDuplicateNameException_whenNameUsedByAnother() {
        UUID id = UUID.randomUUID();
        Correspondent correspondent = new Correspondent("old");
        Correspondent other = new Correspondent("new");
        other.setId(UUID.randomUUID());
        when(correspondentRepository.findById(id)).thenReturn(Optional.of(correspondent));
        when(correspondentRepository.findByName("new")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> correspondentService.update(id, "new"))
            .isInstanceOf(DuplicateNameException.class);

        verify(correspondentRepository, never()).save(any());
    }

    @Test
    void update_throwsIllegalArgument_whenNotFound() {
        UUID id = UUID.randomUUID();
        when(correspondentRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> correspondentService.update(id, "new"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // --- merge ---

    @Test
    void merge_reassignsDocumentsAndDeletesSource() {
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Correspondent source = new Correspondent("Old");
        Correspondent target = new Correspondent("New");
        when(correspondentRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(correspondentRepository.findById(targetId)).thenReturn(Optional.of(target));

        Document doc = new Document();
        doc.setId(UUID.randomUUID());
        doc.setStatus(DocumentStatus.READY);
        doc.setCorrespondent(source);
        when(documentRepository.findAllByCorrespondentId(sourceId)).thenReturn(List.of(doc));

        correspondentService.merge(sourceId, targetId);

        assertThat(doc.getCorrespondent()).isEqualTo(target);
        verify(documentRepository).saveAll(List.of(doc));
        verify(eventPublisher).publishEvent(any(DocumentReadyEvent.class));
        verify(correspondentRepository).delete(source);
    }

    @Test
    void merge_throwsIllegalArgument_whenMergingWithItself() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> correspondentService.merge(id, id))
            .isInstanceOf(IllegalArgumentException.class);

        verify(correspondentRepository, never()).delete(any());
    }
}
