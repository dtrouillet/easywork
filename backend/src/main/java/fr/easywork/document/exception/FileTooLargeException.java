package fr.easywork.document.exception;

public class FileTooLargeException extends RuntimeException {
    public FileTooLargeException(long actualSize, long maxSize) {
        super("File size " + actualSize + " bytes exceeds the maximum allowed size of " + maxSize + " bytes");
    }
}
