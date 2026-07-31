package fr.easywork.document.service;

import fr.easywork.document.domain.Correspondent;
import fr.easywork.document.dto.CorrespondentDto;
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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CorrespondentServiceTest {

    @Mock CorrespondentRepository correspondentRepository;
    @Mock DocumentRepository documentRepository;
    @Mock DocumentMapper mapper;

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
}
