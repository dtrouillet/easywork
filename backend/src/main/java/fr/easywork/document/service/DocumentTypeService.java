package fr.easywork.document.service;

import fr.easywork.document.domain.DocumentType;
import fr.easywork.document.dto.DocumentTypeDto;
import fr.easywork.document.exception.DuplicateNameException;
import fr.easywork.document.exception.EntityInUseException;
import fr.easywork.document.mapper.DocumentMapper;
import fr.easywork.document.repository.DocumentRepository;
import fr.easywork.document.repository.DocumentTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DocumentTypeService {

    private final DocumentTypeRepository documentTypeRepository;
    private final DocumentRepository documentRepository;
    private final DocumentMapper mapper;

    public List<DocumentTypeDto> list() {
        return documentTypeRepository.findAll().stream().map(mapper::toDto).toList();
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public DocumentTypeDto create(String name, Integer retentionDays) {
        if (documentTypeRepository.findByName(name).isPresent()) {
            throw new DuplicateNameException("DocumentType", name);
        }
        DocumentType type = new DocumentType(name);
        type.setRetentionDays(retentionDays);
        return mapper.toDto(documentTypeRepository.save(type));
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public void delete(UUID id) {
        if (documentRepository.existsByDocumentTypeId(id)) {
            throw new EntityInUseException("DocumentType", id);
        }
        documentTypeRepository.deleteById(id);
    }
}
