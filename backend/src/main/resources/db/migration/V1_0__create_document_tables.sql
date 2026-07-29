CREATE TABLE correspondent (
    id          UUID PRIMARY KEY,
    name        VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE document_type (
    id              UUID PRIMARY KEY,
    name            VARCHAR(255) NOT NULL UNIQUE,
    retention_days  INTEGER
);

CREATE TABLE document (
    id                  UUID PRIMARY KEY,
    title               VARCHAR(500) NOT NULL,
    original_filename   VARCHAR(500) NOT NULL,
    mime_type           VARCHAR(127) NOT NULL,
    file_size           BIGINT,
    storage_key         VARCHAR(1000) UNIQUE,
    content_hash        VARCHAR(64),
    extracted_text      TEXT,
    page_count          INTEGER,
    document_date       DATE,
    status              VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    ocr_applied         BOOLEAN NOT NULL DEFAULT FALSE,
    owner_id            VARCHAR(255) NOT NULL,
    correspondent_id    UUID REFERENCES correspondent(id),
    document_type_id    UUID REFERENCES document_type(id),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    archived_at         TIMESTAMP WITH TIME ZONE,
    trashed_at          TIMESTAMP WITH TIME ZONE,
    deleted_at          TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_document_owner_status ON document(owner_id, status);
CREATE INDEX idx_document_created_at   ON document(created_at DESC);
CREATE INDEX idx_document_content_hash ON document(content_hash, owner_id);
