package fr.easywork.document.domain;

import java.util.EnumSet;
import java.util.Set;

public enum DocumentStatus {

    RECEIVED {
        @Override public Set<DocumentStatus> validNextStates() { return EnumSet.of(EXTRACTING); }
    },
    EXTRACTING {
        @Override public Set<DocumentStatus> validNextStates() { return EnumSet.of(OCR, CLASSIFYING); }
    },
    OCR {
        @Override public Set<DocumentStatus> validNextStates() { return EnumSet.of(CLASSIFYING); }
    },
    CLASSIFYING {
        @Override public Set<DocumentStatus> validNextStates() { return EnumSet.of(READY); }
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
    };

    public abstract Set<DocumentStatus> validNextStates();

    public boolean canTransitionTo(DocumentStatus next) {
        return validNextStates().contains(next);
    }
}
