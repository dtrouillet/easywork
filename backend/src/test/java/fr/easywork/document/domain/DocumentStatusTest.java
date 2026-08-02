package fr.easywork.document.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentStatusTest {

    @ParameterizedTest
    @EnumSource(value = DocumentStatus.class, names = {"RECEIVED", "EXTRACTING", "OCR", "CLASSIFYING"})
    void inProgressStates_canTransitionToFailed(DocumentStatus status) {
        assertThat(status.canTransitionTo(DocumentStatus.FAILED)).isTrue();
    }

    @Test
    void failed_canOnlyTransitionBackToReceived() {
        assertThat(DocumentStatus.FAILED.validNextStates()).containsExactly(DocumentStatus.RECEIVED);
    }

    @ParameterizedTest
    @EnumSource(value = DocumentStatus.class, names = {"READY", "ARCHIVED", "TRASH", "DELETED"})
    void terminalOrPostProcessingStates_cannotTransitionToFailed(DocumentStatus status) {
        assertThat(status.canTransitionTo(DocumentStatus.FAILED)).isFalse();
    }
}
