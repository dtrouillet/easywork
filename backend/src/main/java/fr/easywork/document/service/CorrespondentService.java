package fr.easywork.document.service;

import fr.easywork.document.domain.Correspondent;
import fr.easywork.document.dto.CorrespondentDto;
import fr.easywork.document.exception.DuplicateNameException;
import fr.easywork.document.exception.EntityInUseException;
import fr.easywork.document.mapper.DocumentMapper;
import fr.easywork.document.repository.CorrespondentRepository;
import fr.easywork.document.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CorrespondentService {

    private final CorrespondentRepository correspondentRepository;
    private final DocumentRepository documentRepository;
    private final DocumentMapper mapper;

    public List<CorrespondentDto> list() {
        return correspondentRepository.findAll().stream().map(mapper::toDto).toList();
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public CorrespondentDto create(String name) {
        if (correspondentRepository.findByName(name).isPresent()) {
            throw new DuplicateNameException("Correspondent", name);
        }
        return mapper.toDto(correspondentRepository.save(new Correspondent(name)));
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public void delete(UUID id) {
        if (documentRepository.existsByCorrespondentId(id)) {
            throw new EntityInUseException("Correspondent", id);
        }
        correspondentRepository.deleteById(id);
    }
}
