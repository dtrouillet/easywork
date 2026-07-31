-- ADR 0001: replace silent ON DELETE CASCADE on document_tag.tag_id with RESTRICT
-- so that deleting a Tag referenced by at least one document is rejected at the DB
-- level (service layer returns 409 EntityInUse before it reaches this constraint).
--
-- V1_1 created the FK inline (no explicit name), so the auto-generated name differs
-- between PostgreSQL (document_tag_tag_id_fkey) and H2 (system-generated).
-- We recreate the table with explicit constraint names to remain portable.
--
-- Also adds indexes required by the dynamic document-filtering queries introduced
-- in the document-management feature.

-- Step 1: recreate document_tag with an explicit RESTRICT on tag_id
CREATE TABLE document_tag_new (
    document_id UUID NOT NULL,
    tag_id      UUID NOT NULL,
    PRIMARY KEY (document_id, tag_id),
    CONSTRAINT document_tag_document_id_fkey FOREIGN KEY (document_id) REFERENCES document(id) ON DELETE CASCADE,
    CONSTRAINT document_tag_tag_id_fkey      FOREIGN KEY (tag_id)      REFERENCES tag(id)      ON DELETE RESTRICT
);

-- Step 2: carry over any existing rows
INSERT INTO document_tag_new SELECT * FROM document_tag;

-- Step 3: swap
DROP TABLE document_tag;
ALTER TABLE document_tag_new RENAME TO document_tag;

-- Indexes for filtering documents by correspondent, document type and tag
CREATE INDEX idx_document_correspondent ON document(correspondent_id);
CREATE INDEX idx_document_document_type ON document(document_type_id);
CREATE INDEX idx_document_tag_tag       ON document_tag(tag_id);
