package fr.easywork.document.domain;

import java.util.EnumSet;
import java.util.Set;

public enum DocumentStatus {

    RECEIVED {
        @Override public Set<DocumentStatus> validNextStates() { return EnumSet.of(EXTRACTING, FAILED); }
    },
    EXTRACTING {
        @Override public Set<DocumentStatus> validNextStates() { return EnumSet.of(OCR, CLASSIFYING, FAILED); }
    },
    OCR {
        @Override public Set<DocumentStatus> validNextStates() { return EnumSet.of(CLASSIFYING, FAILED); }
    },
    CLASSIFYING {
        @Override public Set<DocumentStatus> validNextStates() { return EnumSet.of(READY, FAILED); }
    },
    READY {
        @Override public Set<DocumentStatus> validNextStates() { return EnumSet.of(ARCHIVED, TRASH); }
    },
    ARCHIVED {
        @Override public Set<DocumentStatus> validNextStates() { return EnumSet.of(READY, TRASH); }
    },
    TRASH {
        @Override public Set<DocumentStatus> validNextStates() { return EnumSet.of(READY, DELETED); }
    },
    DELETED {
        @Override public Set<DocumentStatus> validNextStates() { return EnumSet.noneOf(DocumentStatus.class); }
    },
    /** Terminal-until-retried: extraction/OCR/classification failed. Retry re-enters at RECEIVED. */
    FAILED {
        @Override public Set<DocumentStatus> validNextStates() { return EnumSet.of(RECEIVED); }
    };

    public abstract Set<DocumentStatus> validNextStates();

    public boolean canTransitionTo(DocumentStatus next) {
        return validNextStates().contains(next);
    }
}
