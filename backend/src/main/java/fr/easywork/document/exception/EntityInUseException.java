package fr.easywork.document.exception;

import java.util.UUID;

public class EntityInUseException extends RuntimeException {
    public EntityInUseException(String entityType, UUID id) {
        super(entityType + " " + id + " is still referenced by one or more documents and cannot be deleted");
    }
}
