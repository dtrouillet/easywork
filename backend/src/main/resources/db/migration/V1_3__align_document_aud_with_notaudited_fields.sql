-- ADR 0001: restrict Envers audit scope on Document to non-personal fields only.
--
-- V1_2 created document_aud with title and owner_id, which are now @NotAudited.
-- This migration:
--   1. Scrubs any personal data already written into existing audit revisions.
--   2. Drops the personal-data columns no longer produced by Envers.
--   3. Adds the non-personal audited columns that V1_2 was missing.

-- Step 1 — one-off scrub of personal data in existing revisions
UPDATE document_aud SET title = NULL, owner_id = NULL;

-- Step 2 — remove columns that must not appear in the audit table
ALTER TABLE document_aud DROP COLUMN title;
ALTER TABLE document_aud DROP COLUMN owner_id;

-- Step 3 — add the non-personal audited fields absent from V1_2
ALTER TABLE document_aud ADD COLUMN ocr_applied BOOLEAN;
ALTER TABLE document_aud ADD COLUMN created_at  TIMESTAMP WITH TIME ZONE;
ALTER TABLE document_aud ADD COLUMN updated_at  TIMESTAMP WITH TIME ZONE;
ALTER TABLE document_aud ADD COLUMN archived_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE document_aud ADD COLUMN trashed_at  TIMESTAMP WITH TIME ZONE;
ALTER TABLE document_aud ADD COLUMN deleted_at  TIMESTAMP WITH TIME ZONE;
