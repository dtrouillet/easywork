# easywork
> A Document Management System (DMS) inspired by paperless-ngx — designed to
> be as easy to use for a family as it is robust for an SME.

**Status:** 🚧 Active development — backend + frontend MVP in progress

---

## Running locally

### Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Docker + Docker Compose | 24+ | Runs infrastructure, and the backend by default |
| Node.js | 20.9+ | Use [nvm](https://github.com/nvm-sh/nvm): `nvm use` (reads `.nvmrc`) |
| Java | 21 (LTS) | Only needed for the Maven-based backend alternative below |
| Maven | 3.9+ | Or use the included `./mvnw` wrapper |

---

### 1 — Start infrastructure + backend

```bash
docker compose up -d
```

This builds and starts everything except the frontend: Postgres, MinIO (object
storage), RabbitMQ (async queue), Meilisearch (search), Keycloak (auth), and the
`easywork` backend itself (`document` + `ingest` + `search` modules in one
process, built from `backend/Dockerfile`'s `runtime-ocr` target — includes
Tesseract OCR). The backend connects to the real Postgres container, not H2.

The first run builds the backend image, which takes a few minutes. Wait for
everything to be healthy:

```bash
docker compose ps
```

Health check: http://localhost:8080/actuator/health → `{"status":"UP"}`
Swagger UI: http://localhost:8080/swagger-ui.html

After pulling backend code changes, rebuild and restart just that service:

```bash
docker compose up -d --build easywork
```

> **Alternative — running the backend on the host with Maven** (faster
> iteration, no image rebuild): start only the infra containers, then run the
> backend with the `local` profile (H2 in-memory, no Postgres needed):
> ```bash
> docker compose up -d postgres minio rabbitmq meilisearch keycloak
> cd backend
> SPRING_PROFILES_ACTIVE=local,ingest,search ./mvnw spring-boot:run
> ```
> Don't start the Compose `easywork` service at the same time as this — both
> bind port 8080.

---

### 2 — Keycloak (auto-configured)

Keycloak imports the `easywork` realm automatically on first start from
`keycloak/realm-easywork.json`. No manual setup needed.

Pre-configured out of the box:
- **Realm**: `easywork`
- **Client**: `easywork` (secret: `easywork-secret`, redirect: `http://localhost:3000/*`)
- **Test user**: `dev` / `dev`

The Keycloak H2 database is persisted in a Docker volume (`keycloak_data`), so the
realm survives container restarts. The JSON is only imported once — if the realm
already exists in the volume, Keycloak skips the import.

To reset Keycloak to a clean state: `docker compose down -v` (removes all volumes).

---

### 3 — Configure and start the frontend

Copy the frontend env file — all values are pre-filled for local dev:

```bash
cp frontend/.env.example frontend/.env.local
```

The default values match the realm JSON (client secret `easywork-secret`).
Only `NEXTAUTH_SECRET` can be left as-is for local dev; use a strong random
value in production (`openssl rand -base64 32`).

The frontend runs on the host, not in Docker:

```bash
cd frontend
nvm use        # switches to Node 20
npm install
npm run dev
```

Open http://localhost:3000 — click **Sign in with SSO** and log in with the
`dev` / `dev` test user to authenticate via Keycloak.

---

### Service map

| Service | URL | Credentials |
|---|---|---|
| Frontend | http://localhost:3000 | via Keycloak |
| Backend API | http://localhost:8080 | Bearer token |
| Swagger UI | http://localhost:8080/swagger-ui.html | — |
| Keycloak admin | http://localhost:8180 | admin / admin |
| MinIO console | http://localhost:9001 | minioadmin / minioadmin |
| RabbitMQ mgmt | http://localhost:15672 | guest / guest |
| Meilisearch | http://localhost:7700 | masterKey |

---

## Why this project

[paperless-ngx](https://github.com/paperless-ngx/paperless-ngx) is a great
open-source DMS, but it requires a fair amount of technical configuration
(sorting rules, folders, etc.) to become truly useful.

**easywork** starts from the same need — scan, OCR, classify and retrieve
documents — with two different priorities:

- **A noticeably simpler user experience**, with as little manual
  configuration as possible (automatic classification suggestions instead
  of rules to write)
- **Enterprise-grade architecture from day one**, able to serve both a
  **family** use case (low volume) and an **SME** use case (RBAC, audit,
  compliance), without rewriting anything between the two — only the
  deployment configuration changes.

## Target users

| Profile | Volume | Key needs |
|---|---|---|
| Family | Low | Simplicity, zero configuration, mobile-first |
| SME | Medium to high | RBAC, audit, compliance, integrations |

## Key features

### Import & automatic processing
- Multi-channel import: web/mobile upload, watched folder, email, API
- Duplicate detection **before** processing (saves time compared to
  paperless-ngx)
- Smart OCR, **automatically skipped** when the document already has native
  text
- Real-time processing status (`Received → Extracting → OCR →
  Classifying → Ready`)

### Automatic classification — no rules to configure
- Entity extraction (dates, amounts, IBANs, references) right on receipt
- Progressive learning of the user's habits (a tag applied once is
  automatically suggested for similar documents afterwards)
- Optional LLM-assisted suggestions for ambiguous cases
- Target experience: *"This looks like an EDF invoice — suggested tags:
  Energy, Invoices. Confirm?"*

### Two ways to find your documents
- **Tag view**: filtering by tag and correspondent, ideal for daily use
- **Folder tree view** (`Type → Correspondent → Year`): more intuitive at
  large volumes or when several people share documents without knowing the
  tagging system — mirrors the folder structure used for the Nextcloud
  export

### Integrations — optional connector model
- No mandatory dependency: internal storage works on its own
- **Nextcloud connector**: bidirectional metadata sync (tags, comments) —
  files stay visible and organized natively in Nextcloud, with no change to
  the user's habits
- Other connectors under consideration: network/SMB folder, other DMS
  (migration)

### Document lifecycle
- States: `Active → Archived → Trash → Permanent deletion`
- **Fully automatic retention**, configurable per document type, with no
  manual validation required — with safeguards: documents always pass
  through the trash first, informational notifications, an immutable audit
  log of purges
- GDPR compliance: right to erasure, real deletion from storage and search
  index, deletion traceability kept independently of the deleted document
- Long-term archiving in PDF/A format

## Technical architecture

| Component | Choice |
|---|---|
| Backend | Java / Spring Boot |
| Database | PostgreSQL |
| File storage | MinIO (S3-compatible) |
| Search | Meilisearch / Elasticsearch |
| Async queue | Kafka / RabbitMQ |
| Content extraction | Apache Tika |
| OCR | Tesseract (Tess4J) |
| Observability | Spring Boot Actuator + Micrometer |

```
Frontend (SPA/mobile) → API Gateway (auth, routing)
        │
   ┌────┼────────────────┬──────────────────┐
   ▼    ▼                ▼                  ▼
Doc Service        Ingest/OCR Worker(s)   Search Service
   │                    │                    │
   ▼                    ▼                    ▼
PostgreSQL          Object Store (MinIO)   Search Index
(metadata)          (files)                (Meilisearch/ES)
```

Java/Spring was chosen after an objective comparison with Python/Django,
Node/NestJS, .NET, Go and Rust, on criteria including OCR/document
ecosystem, async handling, RBAC/audit maturity and enterprise adoption —
Spring Security offers the most robust foundation for fine-grained RBAC and
native audit trails (Spring Data Envers), a prerequisite for SME use.

**Point of attention:** JVM memory footprint is heavier than a native
single binary — sizing needs to be considered for minimal deployments (NAS,
Raspberry Pi), with GraalVM native image as a possible option if needed.

## Mockups

The first interactive (React) mockups are available in the project:

- `maquette-accueil.jsx` — home screen / document list, with a toggle
  between tag view and folder tree view, and a structured detail panel
  (information, tags, preview, extracted text, actions)

Visual direction: sober, inspired by physical filing (a colored tab per
category on each document), typography using Fraunces (headings) / Inter
(UI) / IBM Plex Mono (metadata).

## Decision log

| Topic | Decision |
|---|---|
| Simplification goal | Simpler UX/UI for the end user |
| Target usage | Both family AND SME, same architecture |
| Backend language | Java / Spring Boot |
| Nextcloud integration | Bidirectional metadata sync, optional connector |
| Retention expiry | Automatic, configurable per type, no validation |
| Navigation | Two complementary views: tags and folder tree |

## Topics still to explore

- [ ] Full data model (JPA entities)
- [ ] Detailed RBAC / multi-tenant model
- [ ] Import screen and classification suggestion flow
- [ ] Advanced search and navigation
- [ ] Notifications and reminders (due dates, etc.)
- [ ] Permissions/sharing management between members

## License

To be determined.
