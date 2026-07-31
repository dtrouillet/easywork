-- ADR 0001: replace silent ON DELETE CASCADE on document_tag.tag_id with RESTRICT
-- so that deleting a Tag referenced by at least one document is rejected at the DB
-- level (service layer returns 409 EntityInUse before it reaches this constraint).
--
-- Also adds indexes required by the dynamic document-filtering queries introduced
-- in the document-management feature.

-- Replace CASCADE with RESTRICT on tag_id FK
ALTER TABLE document_tag DROP CONSTRAINT document_tag_tag_id_fkey;
ALTER TABLE document_tag ADD CONSTRAINT document_tag_tag_id_fkey
    FOREIGN KEY (tag_id) REFERENCES tag(id) ON DELETE RESTRICT;

-- Indexes for filtering documents by correspondent, document type and tag
CREATE INDEX idx_document_correspondent ON document(correspondent_id);
CREATE INDEX idx_document_document_type ON document(document_type_id);
CREATE INDEX idx_document_tag_tag       ON document_tag(tag_id);
