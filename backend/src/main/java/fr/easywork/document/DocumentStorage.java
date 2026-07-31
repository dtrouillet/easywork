package fr.easywork.document;

import java.io.InputStream;

/** Port: file-level operations on the object store, accessible by other modules. */
public interface DocumentStorage {
    InputStream download(String storageKey);
    void delete(String storageKey);
}
