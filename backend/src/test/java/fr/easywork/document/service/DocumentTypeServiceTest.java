package fr.easywork.document.service;

import fr.easywork.document.domain.Document;
import fr.easywork.document.domain.DocumentStatus;
import fr.easywork.document.domain.DocumentType;
import fr.easywork.document.dto.DocumentTypeDto;
import fr.easywork.document.event.DocumentReadyEvent;
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
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentTypeServiceTest {

    @Mock DocumentTypeRepository documentTypeRepository;
    @Mock DocumentRepository documentRepository;
    @Mock DocumentMapper mapper;
    @Mock ApplicationEventPublisher eventPublisher;

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

    // --- update ---

    @Test
    void update_renamesType_whenNameIsFree() {
        UUID id = UUID.randomUUID();
        DocumentType type = new DocumentType("old");
        when(documentTypeRepository.findById(id)).thenReturn(Optional.of(type));
        when(documentTypeRepository.findByName("new")).thenReturn(Optional.empty());
        when(documentTypeRepository.save(type)).thenReturn(type);

        documentTypeService.update(id, "new", 180);

        assertThat(type.getName()).isEqualTo("new");
        assertThat(type.getRetentionDays()).isEqualTo(180);
    }

    @Test
    void update_throwsDuplicateNameException_whenNameUsedByAnother() {
        UUID id = UUID.randomUUID();
        DocumentType type = new DocumentType("old");
        DocumentType other = new DocumentType("new");
        other.setId(UUID.randomUUID());
        when(documentTypeRepository.findById(id)).thenReturn(Optional.of(type));
        when(documentTypeRepository.findByName("new")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> documentTypeService.update(id, "new", null))
            .isInstanceOf(DuplicateNameException.class);

        verify(documentTypeRepository, never()).save(any());
    }

    @Test
    void update_throwsIllegalArgument_whenNotFound() {
        UUID id = UUID.randomUUID();
        when(documentTypeRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentTypeService.update(id, "new", null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // --- merge ---

    @Test
    void merge_reassignsDocumentsAndDeletesSource() {
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        DocumentType source = new DocumentType("Old");
        DocumentType target = new DocumentType("New");
        when(documentTypeRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(documentTypeRepository.findById(targetId)).thenReturn(Optional.of(target));

        Document doc = new Document();
        doc.setId(UUID.randomUUID());
        doc.setStatus(DocumentStatus.READY);
        doc.setDocumentType(source);
        when(documentRepository.findAllByDocumentTypeId(sourceId)).thenReturn(List.of(doc));

        documentTypeService.merge(sourceId, targetId);

        assertThat(doc.getDocumentType()).isEqualTo(target);
        verify(documentRepository).saveAll(List.of(doc));
        verify(eventPublisher).publishEvent(any(DocumentReadyEvent.class));
        verify(documentTypeRepository).delete(source);
    }

    @Test
    void merge_throwsIllegalArgument_whenMergingWithItself() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> documentTypeService.merge(id, id))
            .isInstanceOf(IllegalArgumentException.class);

        verify(documentTypeRepository, never()).delete(any());
    }
}
