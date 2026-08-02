package fr.easywork.document.service;

import fr.easywork.document.domain.Document;
import fr.easywork.document.domain.DocumentStatus;
import fr.easywork.document.domain.Tag;
import fr.easywork.document.dto.TagDto;
import fr.easywork.document.event.DocumentReadyEvent;
import fr.easywork.document.exception.DuplicateNameException;
import fr.easywork.document.exception.EntityInUseException;
import fr.easywork.document.mapper.DocumentMapper;
import fr.easywork.document.repository.DocumentRepository;
import fr.easywork.document.repository.TagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock TagRepository tagRepository;
    @Mock DocumentRepository documentRepository;
    @Mock DocumentMapper mapper;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks TagService tagService;

    @Test
    void list_returnsAllTags() {
        Tag tag = new Tag("urgent");
        when(tagRepository.findAll()).thenReturn(List.of(tag));
        when(mapper.toDto(tag)).thenReturn(new TagDto(UUID.randomUUID(), "urgent", null));

        var result = tagService.list();

        assertThat(result).hasSize(1);
    }

    @Test
    void create_savesTag_whenNameIsNew() {
        when(tagRepository.findByName("urgent")).thenReturn(Optional.empty());
        Tag saved = new Tag("urgent");
        when(tagRepository.save(any())).thenReturn(saved);
        when(mapper.toDto(saved)).thenReturn(new TagDto(UUID.randomUUID(), "urgent", "#ff0000"));

        tagService.create("urgent", "#ff0000");

        verify(tagRepository).save(any(Tag.class));
    }

    @Test
    void create_throwsDuplicateNameException_whenNameExists() {
        when(tagRepository.findByName("urgent")).thenReturn(Optional.of(new Tag("urgent")));

        assertThatThrownBy(() -> tagService.create("urgent", null))
            .isInstanceOf(DuplicateNameException.class);

        verify(tagRepository, never()).save(any());
    }

    @Test
    void delete_deletesTag_whenNotInUse() {
        UUID id = UUID.randomUUID();
        when(documentRepository.existsByTagsId(id)).thenReturn(false);

        tagService.delete(id);

        verify(tagRepository).deleteById(id);
    }

    @Test
    void delete_throwsEntityInUseException_whenTagReferencedByDocument() {
        UUID id = UUID.randomUUID();
        when(documentRepository.existsByTagsId(id)).thenReturn(true);

        assertThatThrownBy(() -> tagService.delete(id))
            .isInstanceOf(EntityInUseException.class);

        verify(tagRepository, never()).deleteById(any());
    }

    // --- update ---

    @Test
    void update_renamesTag_whenNameIsFreeOrUnchanged() {
        UUID id = UUID.randomUUID();
        Tag tag = new Tag("old");
        when(tagRepository.findById(id)).thenReturn(Optional.of(tag));
        when(tagRepository.findByName("new")).thenReturn(Optional.empty());
        when(tagRepository.save(tag)).thenReturn(tag);

        tagService.update(id, "new", "#ff0000");

        assertThat(tag.getName()).isEqualTo("new");
        assertThat(tag.getColor()).isEqualTo("#ff0000");
    }

    @Test
    void update_throwsDuplicateNameException_whenNameUsedByAnotherTag() {
        UUID id = UUID.randomUUID();
        Tag tag = new Tag("old");
        Tag other = new Tag("new");
        other.setId(UUID.randomUUID());
        when(tagRepository.findById(id)).thenReturn(Optional.of(tag));
        when(tagRepository.findByName("new")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> tagService.update(id, "new", null))
            .isInstanceOf(DuplicateNameException.class);

        verify(tagRepository, never()).save(any());
    }

    @Test
    void update_throwsIllegalArgument_whenTagNotFound() {
        UUID id = UUID.randomUUID();
        when(tagRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tagService.update(id, "new", null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // --- merge ---

    @Test
    void merge_reassignsDocumentsAndDeletesSource() {
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Tag source = new Tag("Old");
        Tag target = new Tag("New");
        when(tagRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(tagRepository.findById(targetId)).thenReturn(Optional.of(target));

        Document doc = new Document();
        doc.setId(UUID.randomUUID());
        doc.setStatus(DocumentStatus.READY);
        doc.setTags(new HashSet<>(Set.of(source)));
        when(documentRepository.findAllByTagsId(sourceId)).thenReturn(List.of(doc));

        tagService.merge(sourceId, targetId);

        assertThat(doc.getTags()).containsExactly(target);
        verify(documentRepository).saveAll(List.of(doc));
        verify(eventPublisher).publishEvent(any(DocumentReadyEvent.class));
        verify(tagRepository).delete(source);
    }

    @Test
    void merge_skipsReindex_whenDocumentNotReadyOrArchived() {
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Tag source = new Tag("Old");
        Tag target = new Tag("New");
        when(tagRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(tagRepository.findById(targetId)).thenReturn(Optional.of(target));

        Document doc = new Document();
        doc.setId(UUID.randomUUID());
        doc.setStatus(DocumentStatus.RECEIVED);
        doc.setTags(new HashSet<>(Set.of(source)));
        when(documentRepository.findAllByTagsId(sourceId)).thenReturn(List.of(doc));

        tagService.merge(sourceId, targetId);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void merge_throwsIllegalArgument_whenMergingTagWithItself() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> tagService.merge(id, id))
            .isInstanceOf(IllegalArgumentException.class);

        verify(tagRepository, never()).delete(any());
    }
}
