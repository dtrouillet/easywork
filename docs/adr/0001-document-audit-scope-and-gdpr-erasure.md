# 0001 — Restrict Envers audit scope on Document and enforce real GDPR erasure

**Status:** Accepted

**Date:** 2026-07-31

## Context

`Document` (`backend/src/main/java/fr/easywork/document/domain/Document.java`) is
annotated `@Audited` at the class level. Hibernate Envers therefore mirrors every
field — including `title`, `originalFilename`, `extractedText` and `ownerId` — into
`document_aud` on every revision. Migration `V1_2__create_audit_tables.sql` only
creates `document_aud` with 6 columns (`id`, `rev`, `revtype`, `title`, `status`,
`owner_id`), so the audit schema is already inconsistent with the entity: Envers
will fail at startup or silently widen the table depending on `ddl-auto`.

`DocumentService.permanentDelete()` only performs a soft delete: it sets
`status = DELETED` and `deletedAt`, deletes the MinIO object, but never deletes the
PostgreSQL row nor scrubs `document_aud`. This directly contradicts the erasure
rule in `CLAUDE.md`: *"GDPR right to erasure: real deletion from PostgreSQL,
MinIO, and search index; only the audit entry survives (without personal data)"*.
As written today, personal data (title, filename, extracted OCR text, owner id)
remains in both the live row and every historical audit revision forever.

Separately, `document_tag` uses `ON DELETE CASCADE` on both foreign keys
(`V1_1__create_tag_tables.sql`), so deleting a `Tag` silently detaches it from
every document. `Correspondent` and `DocumentType` have no cascade rule
(`V1_0__create_document_tables.sql`); deleting a referenced correspondent or
document type today produces an unhandled FK violation (HTTP 500) in
`CorrespondentController`/`DocumentTypeController`. The three reference-data
types need one consistent deletion policy.

## Decision drivers

- GDPR Art. 17 (right to erasure) must be honoured for real, not just for the
  active document list
- Audit trail must stay useful for compliance (who changed what, when) without
  retaining personal data after erasure
- API consumers deserve a predictable, documented error instead of a raw 500
  on FK violations
- Avoid silent data loss (a cascade delete that quietly detaches tags from
  documents is a correctness bug, not just a style issue)

## Considered options

- **Option A** — Keep `@Audited` on the whole `Document` class; scrub `document_aud`
  manually with an `UPDATE ... SET title = NULL, ...` at erasure time.
- **Option B** — Mark personal-data fields `@NotAudited` at the field level so they
  are never written to `document_aud` in the first place; only scrub-and-delete the
  live row at erasure time.
- **Option C** — Drop Envers auditing on `Document` entirely and roll a bespoke
  audit table populated only with non-personal fields.

## Decision outcome

**Chosen option:** Option B — field-level `@NotAudited` on all personal-data
fields (`title`, `originalFilename`, `mimeType`, `fileSize`, `storageKey`,
`contentHash`, `extractedText`, `pageCount`, `documentDate`, `ownerId`, `tags`,
`correspondent`, `documentType`). Only `id`, `status`, `ocrApplied`, `createdAt`,
`updatedAt`, `archivedAt`, `trashedAt`, `deletedAt` stay `@Audited`. This avoids
writing personal data into `document_aud` at all, so no historical scrub is ever
needed for those columns — only the current row must be deleted. Migration
`V1_2` must be replaced by a follow-up migration that drops the columns that are
no longer produced by Envers (`title`, `owner_id`) to keep the audit schema
matching the entity.

`permanentDelete()` must be rewritten to: (1) if any personal-data field was ever
audited before this change ships, run a one-off `UPDATE document_aud SET
title = NULL, owner_id = NULL WHERE id = :id` to scrub pre-existing revisions;
(2) `DELETE FROM document WHERE id = :id` (real row removal, not a status flag);
(3) delete the MinIO object; (4) delete from the Meilisearch index. Steps 2–4 stay
in the existing `DocumentDeletedEvent` flow so search/MinIO cleanup remains
decoupled via Spring Modulith events, but the DB delete must happen synchronously
in the same transaction as the audit scrub, not asynchronously.

For `Tag`, `Correspondent` and `DocumentType`: remove `ON DELETE CASCADE` from
`document_tag` (new migration `V1_4`) and standardise all three controllers to
check for references before deleting, returning `409 Conflict` via a new
`EntityInUseException` mapped in `GlobalExceptionHandler`. No silent cascade,
no unhandled 500.

### Positive consequences

- Erasure requests genuinely remove personal data from all three stores, as
  CLAUDE.md requires
- Audit log stays legally useful (who deleted/restored what, when) without
  holding personal data
- Reference-data deletion behaviour is predictable and documented (409, not 500
  or silent detach)

### Negative consequences / risks

- `@NotAudited` fields lose historical "what did the title used to say" traceability
  — acceptable trade-off since that is exactly the GDPR-sensitive data
  - Mitigation: none needed; this is the intended effect
- Existing `document_aud` rows written before this ADR may already contain
  personal data from `title`/`owner_id`
  - Mitigation: the one-off scrub migration/job runs against all existing rows,
    not just future deletions
- Removing `ON DELETE CASCADE` requires the frontend to handle 409 responses on
  tag/correspondent/document-type deletion (previously cascade succeeded silently)
  - Mitigation: update frontend delete flows to show "still in use, remove from
    documents first" before this ships

## Pros and cons of the options

### Option A — scrub after the fact

**Pros:** entity annotation stays simple (class-level `@Audited`); minimal code change.

**Cons:** personal data is written to `document_aud` on every revision before
being scrubbed — a window where it exists unnecessarily; scrub logic must
enumerate every personal column and stay in sync with the entity forever.

### Option B — field-level `@NotAudited` (chosen)

**Pros:** personal data never lands in the audit table; erasure only needs to
delete the live row; audit table stays minimal and stable.

**Cons:** requires reviewing every new field added to `Document` and explicitly
deciding its audit status; existing audit rows still need a one-off cleanup.

### Option C — bespoke audit table

**Pros:** full control over audit schema, independent from entity shape.

**Cons:** throws away Envers' revision correlation (`revinfo`) used by the
other audited entities (`Tag`, `Correspondent`, `DocumentType`), fragmenting
the audit model; more code to build and maintain for no clear benefit over B.

## Links

- Related code: `backend/src/main/java/fr/easywork/document/domain/Document.java`
- Related code: `backend/src/main/java/fr/easywork/document/service/DocumentService.java`
- Related migration: `backend/src/main/resources/db/migration/V1_2__create_audit_tables.sql`
- Related migration: `backend/src/main/resources/db/migration/V1_1__create_tag_tables.sql`
- CLAUDE.md — GDPR & compliance section
