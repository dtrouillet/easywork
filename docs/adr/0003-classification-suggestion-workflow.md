# 0003 — Replace silent auto-classification with entity extraction, learned signals, and a suggest/confirm workflow

**Status:** Accepted

**Date:** 2026-08-02

## Context

`DocumentStatus` (`backend/src/main/java/fr/easywork/document/domain/DocumentStatus.java`,
lines 17-19) has a `CLASSIFYING` state between `OCR`/`EXTRACTING` and `READY`, but
it is a pure pass-through today: `DocumentService.onIngestCompleted()`
(`backend/src/main/java/fr/easywork/document/service/DocumentService.java`,
lines 219-225) transitions the document into `CLASSIFYING`, calls
`documentClassifier.classify(doc)`, and immediately transitions to `READY` in the
same method — there is no review step. `DocumentClassifier`
(`backend/src/main/java/fr/easywork/document/service/DocumentClassifier.java`,
lines 31-84) is the entirety of today's "automatic classification": a
case-insensitive substring match of every existing correspondent/tag/document-type
*name* against `Document.extractedText`, writing straight into `Document` fields
that are still unset. There is no entity extraction (dates, amounts, IBANs,
reference numbers are never parsed out), no learning from prior corrections, and
no way for a user to see or reject a suggestion before it is applied — README's
"Automatic classification — no rules to configure" differentiator is implemented
today as a silent guess baked directly into the document.

`Document` (`backend/src/main/java/fr/easywork/document/domain/Document.java`) has
no fields for extracted amounts/IBANs/reference numbers; `documentDate` (line 61)
exists but is populated only through manual `PATCH` today.
`POST /api/v1/documents/{id}/reclassify`
(`DocumentController.java`, lines 89-95; `DocumentService.reclassify()`, lines
151-170) already re-runs `DocumentClassifier.classify()` and applies it silently,
returning `DocumentDto`. Its only first-party caller is the "Re-run
auto-classification" button in
`frontend/src/app/(dashboard)/documents/[id]/page.tsx` (lines 101-102, 260-266).
Manual classification goes through `PATCH /api/v1/documents/{id}` with
`DocumentUpdateRequest` (`backend/src/main/java/fr/easywork/document/dto/DocumentUpdateRequest.java`),
whose doc comment is explicit: "All fields are optional — only non-null values
are applied" (line 11) — a pre-existing PATCH-semantics quirk tracked separately
as GitHub issue #22, not addressed here.

`IngestPipeline` (`backend/src/main/java/fr/easywork/ingest/pipeline/IngestPipeline.java`,
`@Profile("ingest")`) runs duplicate-check → Tika extraction → conditional
Tesseract OCR, then returns an `IngestCompletedEvent`
(`backend/src/main/java/fr/easywork/document/event/IngestCompletedEvent.java`) —
deliberately declared inside the `document` package so the dependency stays
unidirectional (`ingest → document`), per ADR 0002. Migrations run up to
`V1_7__add_document_last_ingest_error.sql`; `V1_8`, `V1_9`, `V1_10` are next
available.

This ADR covers Phase 1 only: deterministic entity extraction, correspondent/type
→ tag learning from confirmed corrections, and a suggest/confirm UX. LLM-assisted
suggestions are an explicit Phase 2, deferred to a future ADR 0004.

## Decision drivers

- README promises classification with "no rules to configure", but today's
  implementation applies its best guess with no review step — a wrong guess
  silently misfiles a document and nobody notices until later
- Users must be able to trust what was applied automatically vs. what they
  confirmed themselves
- No new runtime dependency for Phase 1 — entity extraction must not require a
  new NLP library or external service
- Classification should improve from the corrections users already make via
  `PATCH`, without a separate offline training job
- `document_extracted_entity` will hold financial/personal data (IBANs, amounts)
  — GDPR erasure (cascade delete, no Envers auditing) must be designed in from
  the start, per CLAUDE.md
- The `ingest → document` module boundary established in ADR 0002 must not be
  violated — entity extraction runs in `ingest`, but the tables it feeds live in
  `document`

## Considered options

**For entity extraction storage:**
- **Option A** — New nullable columns directly on `Document` (e.g.
  `extractedAmount`, `extractedIban`).
- **Option B** — Separate `document_extracted_entity` child table, one row per
  extracted value.

**For the learning signal:**
- **Option A** — Correspondent-only signal (correspondent → tag).
- **Option B** — Keyword/full-text similarity between documents.
- **Option C** — Correspondent *and* document type as independent signals, both
  → tag.

**For the breaking change to `reclassify`:**
- **Option A** — Keep `/reclassify` silently auto-applying as today; add the new
  suggestion endpoints alongside it.
- **Option B** — Repurpose `/reclassify` in place to regenerate and return the
  suggestion instead of applying it.

## Decision outcome

**Chosen option:** Option B in all three cases.

**Entity extraction.** A new deterministic, regex-based step runs inside
`IngestPipeline` on the text already produced by Tika/Tesseract — no new
dependency. Results (`entityType`, `rawValue`, `normalizedValue`) are carried
across the module boundary as a new field on `IngestCompletedEvent`, the same
mechanism ADR 0002 already uses to keep `ingest → document` unidirectional;
`DocumentService.onIngestCompleted()` persists them into a new
`document_extracted_entity` table (`document_id` FK `ON DELETE CASCADE`,
`entity_type`, `raw_value`, `normalized_value`, `created_at`) rather than new
`Document` columns, because entities are multi-valued (an invoice can have
several dates and amounts) and cascade delete gives GDPR erasure for free. The
best extracted date becomes a *suggested* `documentDate` only — never written
directly, since it is purely manual today and users may rely on that (migration
`V1_8`).

**Progressive learning.** New table `classification_signal_tag` (`signal_type`
`CORRESPONDENT`/`DOCUMENT_TYPE`, `signal_id`, `tag_id` FK `ON DELETE CASCADE`,
`confirmation_count`, `last_confirmed_at`, unique on
`(signal_type, signal_id, tag_id)`) is upserted/incremented whenever
`PATCH /api/v1/documents/{id}` sets both a correspondent (or type) and tags — no
separate training job. At suggestion time, once a correspondent or document type
is matched, associations with `confirmation_count >= 1` become suggested tags,
unioned with the existing substring-matching heuristic. `signal_id` is a
polymorphic reference with no DB-level FK; deleting a correspondent/type can
leave a harmless orphaned row that simply stops matching (migration `V1_9`).

**Suggest/confirm.** `DocumentClassifier` is replaced by a suggestion generator
that no longer mutates `Document`. `onIngestCompleted()` still walks
`CLASSIFYING → READY` exactly as today (no new `DocumentStatus` state — this
stays fully decoupled from ADR 0002's state machine), but a document now reaches
`READY` with whatever it arrived with, plus a `PENDING` row in the new
`document_classification_suggestion` table (`document_id` PK/FK `ON DELETE
CASCADE`, `suggested_correspondent_id`/`suggested_document_type_id` FK `ON
DELETE SET NULL`, `suggested_document_date`, `source`
`HEURISTIC`/`LEARNED` — `LLM` reserved for Phase 2, `status`
`PENDING`/`CONFIRMED`/`REJECTED`, `created_at`, `confirmed_at`, `rejected_at`)
and a join table `document_classification_suggestion_tag` (migration `V1_10`).
New endpoints: `GET .../suggestion`, `POST .../suggestion/confirm` (explicit
per-field accept flags — `acceptCorrespondent`, `acceptDocumentType`,
`acceptDocumentDate`, `acceptTagIds`), `POST .../suggestion/reject` (flips only
the suggestion's status). Confirm deliberately does not reuse
`DocumentUpdateRequest`, so it only ever applies accepted fields and never needs
to express "clear a field" — sidestepping issue #22 entirely for this flow.

`POST /api/v1/documents/{id}/reclassify` is repurposed: it now regenerates and
returns `DocumentClassificationSuggestionDto` (upserting the single
`document_classification_suggestion` row back to `PENDING`) instead of silently
applying and returning `DocumentDto`. This is a breaking response-shape change
inside `/api/v1`, accepted because there is exactly one first-party caller — the
frontend button in `page.tsx` — updated in the same PR, and because maintaining
a second, divergent auto-apply code path alongside the new suggestion path would
directly undermine the point of this ADR.

No backfill: documents already `READY` keep whatever `DocumentClassifier`
already silently wrote before this ships. There is nothing meaningful to
retroactively "confirm" for historical documents, and generating suggestions for
them risks relitigating classifications users have implicitly already accepted.

**GDPR impact note:** `document_extracted_entity` stores newly-extracted
financial/personal data (IBANs, amounts, reference numbers) and must cascade
delete with the document; the whole entity is intentionally left un-`@Audited`
(no Envers revision history at all), consistent with ADR 0001's principle that
personal data must never enter `*_aud` tables, and it must never appear in logs
per CLAUDE.md's security standards. `document_classification_suggestion` and
`document_classification_suggestion_tag` hold no new personal data beyond
references to existing taxonomy entities and are also cascade-deleted with the
document.

### Positive consequences

- Wrong guesses are visible and rejectable before they land on the document,
  instead of being discovered later as a silent misfile
- Real new data (amounts, IBANs, reference numbers, candidate dates) becomes
  available with zero new runtime infrastructure
- Suggestions improve over time purely from corrections users already make via
  `PATCH` — no offline training job to run or monitor
- The confirm endpoint's explicit accept flags avoid the PATCH null-semantics
  quirk (#22) entirely for this flow

### Negative consequences / risks

- Breaking response-shape change on `/reclassify` inside `/api/v1`, with no
  deprecation window
  - Mitigation: only the frontend calls it today, updated in the same PR; a
    second consumer appearing later would require a proper deprecation ADR
    before repeating this pattern
- No backfill means old and newly-ingested documents behave inconsistently
  (old ones already silently classified, new ones sit `PENDING` until
  confirmed)
  - Mitigation: accepted as forward-looking only; nothing meaningful to
    confirm retroactively
- `classification_signal_tag.signal_id` has no DB-level FK, so deleting a
  correspondent/document type can leave an orphaned row
  - Mitigation: harmless — the row simply never matches again; a periodic
    cleanup job is not built now
- Regex-based entity extraction is initially tuned for common formats (IBAN,
  ISO/`DD/MM/YYYY` dates, `€` amounts) and will miss other locales/formats
  - Mitigation: none yet; broader format coverage is a follow-up, not a
    blocker for Phase 1
- Two classification code paths existed only transiently during this change
  (old `DocumentClassifier` mutation logic vs. the new suggestion generator);
  the old path is fully removed, not left running in parallel

## Pros and cons of the options

### Entity extraction storage — Option A: columns on `Document`

**Pros:** no join needed to read the latest extracted value.

**Cons:** entities are multi-valued (an invoice can have an issue date, a due
date, and several line-item amounts) — collapsing them into fixed columns loses
information or forces ad-hoc `amount2`/`amount3` columns; every new personal-data
column on `Document` also grows the field-by-field `@NotAudited` review ADR 0001
already established as an ongoing maintenance burden.

### Entity extraction storage — Option B: child table (chosen)

**Pros:** naturally multi-valued; `ON DELETE CASCADE` gives GDPR erasure for
free; keeps `Document` itself unchanged, so ADR 0001's audit review doesn't grow.

**Cons:** extra join for read paths that want "the best date"; extraction
metadata (type, normalization) lives outside the `Document` entity.

### Learning signal — Option A: correspondent-only

**Pros:** simplest, a single lookup.

**Cons:** correspondents that send several kinds of documents (e.g. a bank
sending both statements and letters) get less precise tag suggestions; adding
document type as a second, equally cheap signal was not meaningfully harder.

### Learning signal — Option B: keyword/full-text similarity

**Pros:** doesn't require a correspondent to already be identified; could
generalise to any recurring pattern, not just correspondent/type.

**Cons:** needs a real similarity index (TF-IDF, embeddings) — genuine new
complexity/infra for Phase 1, when correspondent matching already gives a
strong, explainable signal (README's own example: "this looks like an EDF
invoice"). Rejected as premature for Phase 1, not precluded later — likely a
natural fit alongside the Phase 2 LLM work in the future ADR 0004.

### Learning signal — Option C: correspondent + document type (chosen)

**Pros:** reuses the correspondent match already computed for the correspondent
suggestion; a second stable, explainable signal ("EDF documents are usually
tagged 'Energy'") at the cost of one more row shape in the same table.

**Cons:** a brand-new correspondent or type still gets no learned suggestions on
its first document — falls back to the heuristic substring match only.

### Reclassify breaking change — Option A: keep both endpoints

**Pros:** zero breaking change; any existing caller keeps working unmodified.

**Cons:** two classification code paths (silent auto-apply and reviewable
suggestion) would have to keep matching logic in sync forever, and the old
endpoint would keep silently mutating documents for any caller still using it —
directly undermining the purpose of this ADR.

### Reclassify breaking change — Option B: repurpose in place (chosen)

**Pros:** one classification code path; forces the one existing caller onto the
reviewable flow immediately; smaller total surface area to maintain.

**Cons:** breaking change with no deprecation window — acceptable only because
there is exactly one first-party consumer, updated in the same PR.

## Links

- Related code: `backend/src/main/java/fr/easywork/document/domain/DocumentStatus.java`
- Related code: `backend/src/main/java/fr/easywork/document/domain/Document.java`
- Related code: `backend/src/main/java/fr/easywork/document/service/DocumentService.java`
- Related code: `backend/src/main/java/fr/easywork/document/service/DocumentClassifier.java`
- Related code: `backend/src/main/java/fr/easywork/document/api/DocumentController.java`
- Related code: `backend/src/main/java/fr/easywork/document/dto/DocumentUpdateRequest.java`
- Related code: `backend/src/main/java/fr/easywork/document/event/IngestCompletedEvent.java`
- Related code: `backend/src/main/java/fr/easywork/ingest/pipeline/IngestPipeline.java`
- Related code: `frontend/src/app/(dashboard)/documents/[id]/page.tsx`
- Related migrations: `backend/src/main/resources/db/migration/V1_8__create_document_extracted_entity_table.sql`,
  `V1_9__create_classification_signal_tag_table.sql`,
  `V1_10__create_document_classification_suggestion_tables.sql`
- Related: [0001](0001-document-audit-scope-and-gdpr-erasure.md) — GDPR erasure
  and audit-scope conventions this ADR follows for `document_extracted_entity`
- Related: [0002](0002-document-processing-workflow.md) — `ingest → document`
  unidirectional module boundary and event-carried data pattern reused here
- Deferred: future ADR 0004 — LLM-assisted classification suggestions (Phase 2),
  not designed in this ADR
- GitHub issue #22 — `PATCH` null-semantics quirk, deliberately not reused by
  the new confirm endpoint
- CLAUDE.md — "Automatic classification suggestions" differentiator; GDPR &
  compliance section; Flyway rules
