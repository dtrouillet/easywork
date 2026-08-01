package fr.easywork.document.exception;

public class UnsupportedMimeTypeException extends RuntimeException {
    public UnsupportedMimeTypeException(String detectedMimeType) {
        super("Unsupported file type: " + detectedMimeType);
    }
}
