package fr.easywork.document.service;

import fr.easywork.document.domain.Tag;
import fr.easywork.document.dto.TagDto;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock TagRepository tagRepository;
    @Mock DocumentRepository documentRepository;
    @Mock DocumentMapper mapper;

    @InjectMocks TagService tagService;

    @Test
    void list_returnsAllTags() {
        Tag tag = new Tag("urgent");
        when(tagRepository.findAll()).thenReturn(List.of(tag));
        when(mapper.toDto(tag)).thenReturn(new TagDto(UUID.randomUUID(), "urgent", null));

        var result = tagService.list();

        assert result.size() == 1;
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
}
