package fr.easywork.document.mapper;

import fr.easywork.document.domain.Correspondent;
import fr.easywork.document.domain.Document;
import fr.easywork.document.domain.DocumentClassificationSuggestion;
import fr.easywork.document.domain.DocumentType;
import fr.easywork.document.domain.Tag;
import fr.easywork.document.dto.CorrespondentDto;
import fr.easywork.document.dto.DocumentClassificationSuggestionDto;
import fr.easywork.document.dto.DocumentDto;
import fr.easywork.document.dto.DocumentTypeDto;
import fr.easywork.document.dto.TagDto;
import org.mapstruct.Mapper;

@Mapper
public interface DocumentMapper {

    DocumentDto toDto(Document document);

    TagDto toDto(Tag tag);

    CorrespondentDto toDto(Correspondent correspondent);

    DocumentTypeDto toDto(DocumentType documentType);

    DocumentClassificationSuggestionDto toDto(DocumentClassificationSuggestion suggestion);
}
