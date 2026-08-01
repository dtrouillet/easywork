package fr.easywork.document.service;

import fr.easywork.document.config.UploadProperties;
import fr.easywork.document.exception.EmptyFileException;
import fr.easywork.document.exception.FileTooLargeException;
import fr.easywork.document.exception.StorageException;
import fr.easywork.document.exception.UnsupportedMimeTypeException;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Component
@RequiredArgsConstructor
class UploadValidator {

    private final UploadProperties props;
    private final Tika tika = new Tika();

    String validate(MultipartFile file) {
        if (file.isEmpty()) {
            throw new EmptyFileException();
        }
        if (file.getSize() > props.maxFileSizeBytes()) {
            throw new FileTooLargeException(file.getSize(), props.maxFileSizeBytes());
        }

        String detectedMimeType;
        try {
            detectedMimeType = tika.detect(file.getInputStream(), file.getOriginalFilename());
        } catch (IOException e) {
            throw new StorageException("Failed to read uploaded file for MIME detection", e);
        }

        if (!props.allowedMimeTypes().contains(detectedMimeType)) {
            throw new UnsupportedMimeTypeException(detectedMimeType);
        }
        return detectedMimeType;
    }
}
