package fr.easywork.document.exception;

public class EmptyFileException extends RuntimeException {
    public EmptyFileException() {
        super("Uploaded file is empty");
    }
}
