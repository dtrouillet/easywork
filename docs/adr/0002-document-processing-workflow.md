# 0002 — Harden the document processing workflow: failure state, duplicate-first ordering, PDF OCR

**Status:** Accepted

**Date:** 2026-08-02

## Context

The document lifecycle (`DocumentStatus` in
`backend/src/main/java/fr/easywork/document/domain/DocumentStatus.java`) drives
every uploaded file through `RECEIVED → EXTRACTING → (OCR) → CLASSIFYING →
READY → ARCHIVED/TRASH → DELETED`. The `ingest` module (profile `ingest`) does
the actual work in `IngestPipeline`: download from MinIO, hash the content,
extract native text with Tika, OCR with Tesseract if needed, then publish an
`IngestCompletedEvent` that `DocumentService.onIngestCompleted()` — an
`@ApplicationModuleListener` in the `document` module — consumes to advance the
state machine.

Three concrete problems surfaced in this pipeline before this change:

1. **No failure path.** `IngestCompletedEvent` could report a Tika/Tesseract
   exception, but `DocumentStatus` had no state to represent it — a failed
   document had nowhere to go, and there was no way for a user to retry it.
2. **Wasted work on duplicates.** The duplicate check (hash lookup against
   `document.content_hash`) only ran *after* extraction and OCR had already
   completed, inside `DocumentService.onIngestCompleted()`. For a large scanned
   PDF this meant running the full Tesseract pipeline — the most CPU-expensive
   step in the whole system — on a file that was about to be deleted as a
   duplicate. CLAUDE.md lists "duplicate detection before processing" as an
   explicit differentiator vs. paperless-ngx; the implementation contradicted it.
3. **OCR was silently broken for the primary use case.** `OcrProcessor` called
   `ImageIO.read()` on the raw file bytes for every mime type. `ImageIO` cannot
   decode PDF bytes at all, so `image` was `null` and `Tesseract.doOCR(null)`
   either threw an opaque NPE or produced empty text — for scanned PDFs, which
   are the dominant document type this system is built to handle. Page count was
   also never populated (`pageCount` stayed `null` forever).

## Decision drivers

- Ingest failures must be visible and recoverable without a database console
- CPU-bound work (OCR) must never run on a file we already know we're going to
  discard as a duplicate
- Module boundaries (Spring Modulith, enforced by
  `EasyworkApplicationTests.modulesAreStructurallyValid()` calling
  `ApplicationModules.verify()`) must stay respected — `ingest` cannot reach
  into `document`'s repositories directly
- Scanned PDFs are the primary real-world input; OCR must actually work on them
- No personal data — including document content — may leak into technical
  fields that are exempt from GDPR erasure semantics or from audit scrubbing

## Considered options

**For the failure state:**
- **Option A** — Add `FAILED` with manual-only retry (`FAILED → RECEIVED`,
  triggered by a new `POST /api/v1/documents/{id}/retry` endpoint).
- **Option B** — Add `FAILED` with automatic retry and exponential backoff
  built into the ingest listener.
- **Option C** — No new state; leave the document stuck in its last
  in-progress state (e.g. `OCR`) on failure and log the error.

**For duplicate detection ordering:**
- **Option A** — New port `DocumentDuplicateCheck` in the top-level
  `fr.easywork.document` package (same pattern as the existing `DocumentStorage`
  port), implemented by `DocumentService`, called by `IngestPipeline` right
  after hashing and before extraction.
- **Option B** — Give the `ingest` module direct access to
  `DocumentRepository`/`DocumentService` internals.
- **Option C** — Keep the duplicate check where it was (after OCR), but make it
  faster by hashing earlier in the flow.

**For OCR on PDFs:**
- **Option A** — Rasterize each PDF page with Apache PDFBox
  (`PDDocument.load` + `PDFRenderer.renderImageWithDPI` at 300 DPI, `ImageType.GRAY`)
  before handing each page image to Tess4J.
- **Option B** — Shell out to an external `pdftoppm`/Poppler binary to convert
  PDF pages to images before OCR.
- **Option C** — Rely on Tika's OCR-on-PDF integration (`tika-parser-ocr-module`)
  to run Tesseract internally instead of orchestrating it ourselves.

## Decision outcome

**Chosen option:** Option A in all three cases.

- `FAILED` is a first-class status reachable from `RECEIVED`, `EXTRACTING`,
  `OCR`, and `CLASSIFYING`; its only valid transition out is back to `RECEIVED`,
  triggered exclusively by the new `retryIngest()` service method and
  `POST /api/v1/documents/{id}/retry` endpoint (204 on success, 422 via the
  existing `GlobalExceptionHandler` `IllegalStateException` mapping if the
  document isn't currently `FAILED`). Automatic retry/backoff is explicitly
  deferred — see risks below.
- `DocumentDuplicateCheck` is a narrow port
  (`existsDuplicate(contentHash, ownerId, excludingDocumentId)`) that
  `DocumentService` implements and `IngestPipeline` depends on, mirroring the
  existing `DocumentStorage` port. `IngestPipeline.process()` now hashes the
  downloaded bytes, checks for a duplicate immediately, and short-circuits
  extraction/OCR entirely if one is found — `DocumentService.onIngestCompleted()`
  still performs the authoritative delete-and-reject as the single source of
  truth for what counts as a duplicate.
- `OcrProcessor` now branches on mime type: `application/pdf` is rasterized
  page-by-page with PDFBox before each page image goes through Tesseract;
  other mime types keep using `ImageIO.read()`, now throwing a clear
  `IOException` instead of silently passing a `null` image to Tesseract when
  decoding fails. `ContentExtractor` now reads `pageCount` from Tika's
  `xmpTPg:NPages` metadata instead of leaving it `null`. PDFBox was already
  present transitively, but pinned at two incompatible major versions at once:
  `tika-parser-pdf-module` (`${tika.version}` = 2.9.2) is compiled against
  PDFBox 2.0.31's `PDDocument.load()` API, while `tess4j` (5.20.0) transitively
  pulls `pdfbox-tools` 3.x for its own unused PDF-to-image utility — and 3.x
  removed `PDDocument.load()` in favor of `Loader.loadPDF()`. Left alone,
  Maven's dependency mediation picked the 3.x line for the whole build, which
  compiled fine but broke Tika's PDF text extraction at runtime with a
  `NoSuchMethodError` the moment a test actually exercised it (the 0%-coverage
  ingest module had never triggered this before). Fixed by excluding
  `pdfbox-tools` from the `tess4j` dependency in `backend/pom.xml` and pinning
  `pdfbox.version=2.0.31` explicitly, matching what `tika-parent:2.9.2`
  declares — `OcrProcessor` uses `PDDocument.load()`, not `Loader.loadPDF()`,
  accordingly.

**GDPR note:** the new `Document.lastIngestError` field (migration
`V1_7__add_document_last_ingest_error.sql`, additive/nullable, no deprecation
ADR needed) is `@NotAudited` and nulled in `scrubPersonalData()`, consistent
with ADR 0001. It is populated directly from `IngestCompletedEvent.errorMessage()`,
itself `e.getMessage()` from exceptions caught in `IngestPipeline` — i.e.
bounded framework/library messages, never document content. Future
contributors must not extend this field to carry extracted text, filenames, or
any other document-derived data; that would violate the "no personal data in
logs/technical fields" rule in CLAUDE.md's security standards.

### Positive consequences

- Ingest failures are visible in the API and recoverable by the document owner
  without direct database access
- Duplicate uploads no longer burn CPU on Tika/Tesseract for a file about to be
  deleted — matches the CLAUDE.md differentiator as actually implemented
- Scanned PDFs — the primary use case — now OCR correctly instead of silently
  failing or producing empty text
- Page counts are now populated for extracted/OCR'd documents
- Module boundaries stay unidirectional (`ingest` → `document`), verified by
  `ApplicationModules.verify()` in CI

### Negative consequences / risks

- Retry is manual only; a transient OCR backend outage requires the user (or
  an operator script) to call `/retry` on every affected document
  - Mitigation: deferred by design — automatic retry needs backoff/circuit-breaking
    to avoid retry storms against a still-broken backend; tracked as follow-up work,
    not solved here
- Multi-page TIFF images still only OCR the first frame `ImageIO.read()`
  decodes; multi-page TIFF is not handled by the PDFBox branch (PDF-only)
  - Mitigation: none yet; tracked as a known gap, low volume expected vs. PDF
- No ClamAV/malware scan runs anywhere in the ingest pipeline yet, despite
  being listed as a requirement in CLAUDE.md's security standards
  - Mitigation: none yet; must land before this pipeline is considered
    production-hardened for untrusted uploads
- `pdfbox.version` is manually pinned to `tika-parent`'s own PDFBox version and
  the `tess4j` → `pdfbox-tools` exclusion must both stay in sync whenever
  `tika.version` or `tess4j.version` is bumped, or the `NoSuchMethodError`
  described above resurfaces silently (it only shows up when PDF parsing code
  actually runs — exactly the gap that let it go unnoticed until this change)
  - Mitigation: a comment in `pom.xml` documents the check (inspect
    `tika-parent`'s `<pdfbox.version>` in the local Maven repo) and
    `ContentExtractorTest`/`OcrProcessorTest` now exercise real PDF parsing on
    every build, so a future mismatch fails CI instead of shipping silently

## Pros and cons of the options

### Failure state — Option A: manual retry only (chosen)

**Pros:** simple, predictable, no risk of retry storms; gives the user
visibility and control immediately.

**Cons:** no self-healing for transient failures; an operator or the user must
notice and act.

### Failure state — Option B: automatic retry with backoff

**Pros:** self-heals from transient failures without user action.

**Cons:** needs backoff/circuit-breaker design to avoid hammering a broken OCR
backend; meaningfully larger scope than this change; deferred rather than
rushed.

### Failure state — Option C: no new state

**Pros:** no schema/state-machine change.

**Cons:** a failed document stays stuck in an in-progress-looking state
forever with no error message and no recovery path — status quo, rejected.

### Duplicate ordering — Option A: `DocumentDuplicateCheck` port (chosen)

**Pros:** keeps the `ingest → document` dependency unidirectional, exactly
like the existing `DocumentStorage` port; passes Spring Modulith's structural
verification; skips CPU-expensive work as early as possible.

**Cons:** one more small interface to maintain; duplicate logic now exists in
two places (the early port-based check in `IngestPipeline`, and the
authoritative check in `onIngestCompleted()`) — acceptable because the
authoritative one is still the single source of truth for rejection.

### Duplicate ordering — Option B: direct repository access from `ingest`

**Pros:** no new interface.

**Cons:** creates a reverse/direct dependency from `ingest` into `document`
internals, which `ApplicationModules.verify()` would reject — rejected outright.

### OCR — Option A: PDFBox rasterization (chosen)

**Pros:** pure-JVM, no external process dependency; PDFBox was already on the
classpath transitively via Tika; fine-grained control over DPI/color mode for
OCR quality.

**Cons:** rendering at 300 DPI is memory/CPU-intensive for large multi-page
PDFs; version must be kept aligned with Tika's transitive resolution.

### OCR — Option B: external Poppler binary

**Pros:** mature, fast, widely used for this exact purpose.

**Cons:** adds a native binary dependency to every ingest-worker image/pod,
complicating the Dockerfile and Helm chart for no benefit over an in-JVM
solution that was already available.

### OCR — Option C: Tika's own OCR-on-PDF integration

**Pros:** less orchestration code in `OcrProcessor`.

**Cons:** less control over per-page DPI/threshold tuning; couples the
"smart OCR skip" decision (native-text-length heuristic in `ContentExtractor`)
awkwardly with Tika's internal OCR trigger, which was designed for a different
use case (opportunistic OCR during parsing, not our two-phase extract-then-OCR flow).

## Links

- Related code: `backend/src/main/java/fr/easywork/document/domain/DocumentStatus.java`
- Related code: `backend/src/main/java/fr/easywork/document/domain/Document.java`
- Related code: `backend/src/main/java/fr/easywork/document/DocumentDuplicateCheck.java`
- Related code: `backend/src/main/java/fr/easywork/document/service/DocumentService.java`
- Related code: `backend/src/main/java/fr/easywork/document/api/DocumentController.java`
- Related code: `backend/src/main/java/fr/easywork/ingest/pipeline/IngestPipeline.java`
- Related code: `backend/src/main/java/fr/easywork/ingest/pipeline/ContentExtractor.java`
- Related code: `backend/src/main/java/fr/easywork/ingest/pipeline/OcrProcessor.java`
- Related migration: `backend/src/main/resources/db/migration/V1_7__add_document_last_ingest_error.sql`
- Related dependency: `backend/pom.xml` (`pdfbox.version`)
- Related: [0001](0001-document-audit-scope-and-gdpr-erasure.md) — GDPR erasure and audit scope conventions this ADR follows for `lastIngestError`
- CLAUDE.md — "Automatic classification suggestions", "Duplicate detection before
  processing", "Smart OCR skipping" differentiators; Security standards (malware
  scanning, no personal data in logs)
