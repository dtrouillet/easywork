-- ADR 0003: progressive learning. Every manual PATCH that sets both a
-- correspondent (or document type) and tags upserts/increments a
-- signal -> tag association here; suggestion generation reads it back to
-- suggest tags a user has already confirmed for similar documents before.
--
-- signal_id is a polymorphic reference (a correspondent id or a document type
-- id, disambiguated by signal_type) with no DB-level FK — accepted as minor
-- debt in ADR 0003: deleting a correspondent/type just leaves a harmless
-- orphaned row that stops matching. tag_id cascades because this table is
-- internal derived data, not user-visible content that should block a tag
-- deletion (deliberately diverging from document_tag's RESTRICT, ADR 0001).
CREATE TABLE classification_signal_tag (
    id                  UUID PRIMARY KEY,
    signal_type         VARCHAR(20) NOT NULL,
    signal_id           UUID NOT NULL,
    tag_id              UUID NOT NULL,
    confirmation_count  INTEGER NOT NULL DEFAULT 0,
    last_confirmed_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT classification_signal_tag_tag_id_fkey
        FOREIGN KEY (tag_id) REFERENCES tag(id) ON DELETE CASCADE,
    CONSTRAINT classification_signal_tag_unique
        UNIQUE (signal_type, signal_id, tag_id)
);

CREATE INDEX idx_classification_signal_tag_signal ON classification_signal_tag(signal_type, signal_id);
