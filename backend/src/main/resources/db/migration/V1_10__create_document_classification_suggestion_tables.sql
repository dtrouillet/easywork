-- ADR 0003: suggest/confirm workflow. One PENDING/CONFIRMED/REJECTED suggestion
-- per document (document_id is both PK and FK), replacing today's silent
-- auto-apply. Correspondent/type references are ON DELETE SET NULL rather than
-- CASCADE — deleting a correspondent/type shouldn't delete a still-pending
-- suggestion for an unrelated document, only clear the dangling reference.
CREATE TABLE document_classification_suggestion (
    document_id                    UUID PRIMARY KEY,
    suggested_correspondent_id     UUID,
    suggested_document_type_id     UUID,
    suggested_document_date        DATE,
    source                         VARCHAR(20) NOT NULL,
    status                          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at                     TIMESTAMP WITH TIME ZONE NOT NULL,
    confirmed_at                   TIMESTAMP WITH TIME ZONE,
    rejected_at                    TIMESTAMP WITH TIME ZONE,
    CONSTRAINT document_classification_suggestion_document_id_fkey
        FOREIGN KEY (document_id) REFERENCES document(id) ON DELETE CASCADE,
    CONSTRAINT document_classification_suggestion_correspondent_id_fkey
        FOREIGN KEY (suggested_correspondent_id) REFERENCES correspondent(id) ON DELETE SET NULL,
    CONSTRAINT document_classification_suggestion_document_type_id_fkey
        FOREIGN KEY (suggested_document_type_id) REFERENCES document_type(id) ON DELETE SET NULL
);

CREATE TABLE document_classification_suggestion_tag (
    document_id UUID NOT NULL,
    tag_id      UUID NOT NULL,
    PRIMARY KEY (document_id, tag_id),
    CONSTRAINT document_classification_suggestion_tag_document_id_fkey
        FOREIGN KEY (document_id) REFERENCES document_classification_suggestion(document_id) ON DELETE CASCADE,
    CONSTRAINT document_classification_suggestion_tag_tag_id_fkey
        FOREIGN KEY (tag_id) REFERENCES tag(id) ON DELETE CASCADE
);
