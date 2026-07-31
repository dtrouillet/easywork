package fr.easywork.document.service;

import fr.easywork.document.domain.DocumentType;
import fr.easywork.document.dto.DocumentTypeDto;
import fr.easywork.document.exception.DuplicateNameException;
import fr.easywork.document.exception.EntityInUseException;
import fr.easywork.document.mapper.DocumentMapper;
import fr.easywork.document.repository.DocumentRepository;
import fr.easywork.document.repository.DocumentTypeRepository;
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
class DocumentTypeServiceTest {

    @Mock DocumentTypeRepository documentTypeRepository;
    @Mock DocumentRepository documentRepository;
    @Mock DocumentMapper mapper;

    @InjectMocks DocumentTypeService documentTypeService;

    @Test
    void create_savesType_withRetentionDays() {
        when(documentTypeRepository.findByName("Facture")).thenReturn(Optional.empty());
        DocumentType saved = new DocumentType("Facture");
        saved.setRetentionDays(365);
        when(documentTypeRepository.save(any())).thenReturn(saved);
        when(mapper.toDto(saved)).thenReturn(new DocumentTypeDto(UUID.randomUUID(), "Facture", 365));

        documentTypeService.create("Facture", 365);

        verify(documentTypeRepository).save(argThat(t -> t.getRetentionDays() == 365));
    }

    @Test
    void create_throwsDuplicateNameException_whenNameExists() {
        when(documentTypeRepository.findByName("Facture")).thenReturn(Optional.of(new DocumentType("Facture")));

        assertThatThrownBy(() -> documentTypeService.create("Facture", null))
            .isInstanceOf(DuplicateNameException.class);

        verify(documentTypeRepository, never()).save(any());
    }

    @Test
    void delete_deletesType_whenNotInUse() {
        UUID id = UUID.randomUUID();
        when(documentRepository.existsByDocumentTypeId(id)).thenReturn(false);

        documentTypeService.delete(id);

        verify(documentTypeRepository).deleteById(id);
    }

    @Test
    void delete_throwsEntityInUseException_whenReferencedByDocument() {
        UUID id = UUID.randomUUID();
        when(documentRepository.existsByDocumentTypeId(id)).thenReturn(true);

        assertThatThrownBy(() -> documentTypeService.delete(id))
            .isInstanceOf(EntityInUseException.class);

        verify(documentTypeRepository, never()).deleteById(any());
    }
}
