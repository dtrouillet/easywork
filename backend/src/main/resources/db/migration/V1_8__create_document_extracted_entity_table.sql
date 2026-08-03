-- ADR 0003: deterministic entity extraction (dates, amounts, IBANs, reference
-- numbers) runs in the ingest pipeline and is persisted as one row per value,
-- since a single document can carry several dates/amounts. Cascade delete keeps
-- this financial/personal data covered by GDPR erasure without any extra code
-- in DocumentService.permanentDelete() (ADR 0001).
CREATE TABLE document_extracted_entity (
    id                  UUID PRIMARY KEY,
    document_id         UUID NOT NULL,
    entity_type         VARCHAR(20) NOT NULL,
    raw_value           VARCHAR(500) NOT NULL,
    normalized_value    VARCHAR(500),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT document_extracted_entity_document_id_fkey
        FOREIGN KEY (document_id) REFERENCES document(id) ON DELETE CASCADE
);

CREATE INDEX idx_document_extracted_entity_document ON document_extracted_entity(document_id);
