-- Processing workflow ADR 0002: FAILED status + manual retry. When ingest fails,
-- the document needs a visible, bounded technical error message so the owner can
-- decide whether to retry. Additive, nullable column — no deprecation ADR needed.
ALTER TABLE document ADD COLUMN last_ingest_error VARCHAR(2000);
