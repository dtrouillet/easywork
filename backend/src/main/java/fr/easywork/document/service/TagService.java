package fr.easywork.document.service;

import fr.easywork.document.domain.Tag;
import fr.easywork.document.dto.TagDto;
import fr.easywork.document.exception.DuplicateNameException;
import fr.easywork.document.exception.EntityInUseException;
import fr.easywork.document.mapper.DocumentMapper;
import fr.easywork.document.repository.DocumentRepository;
import fr.easywork.document.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;
    private final DocumentRepository documentRepository;
    private final DocumentMapper mapper;

    public List<TagDto> list() {
        return tagRepository.findAll().stream().map(mapper::toDto).toList();
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public TagDto create(String name, String color) {
        if (tagRepository.findByName(name).isPresent()) {
            throw new DuplicateNameException("Tag", name);
        }
        Tag tag = new Tag(name);
        tag.setColor(color);
        return mapper.toDto(tagRepository.save(tag));
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public void delete(UUID id) {
        if (documentRepository.existsByTagsId(id)) {
            throw new EntityInUseException("Tag", id);
        }
        tagRepository.deleteById(id);
    }
}
