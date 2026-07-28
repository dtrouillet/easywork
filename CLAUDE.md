# CLAUDE.md — easywork

## Project overview

**easywork** is an enterprise-grade Document Management System (DMS) inspired by
[paperless-ngx](https://github.com/paperless-ngx/paperless-ngx). It centralises,
OCRs, classifies and retrieves documents with as little manual configuration as
possible.

Dual target: **family** (simplicity, zero config, mobile-first) and **SME** (RBAC,
audit, compliance) — same codebase, configuration-only differences between
deployments.

Key differentiators vs. paperless-ngx:
- Automatic classification suggestions (no rules to write)
- Duplicate detection before processing
- Smart OCR skipping when native text already exists
- Two navigation views: tag view and folder tree (`Type → Correspondent → Year`)
- Optional Nextcloud connector (bidirectional metadata sync)

---

## Repository structure

This is a **monorepo**. Never collapse applications into one module.

```
easywork/
├── backend/                  # Spring Boot API + domain services
│   ├── doc-service/          # Document metadata, lifecycle, RBAC
│   ├── ingest-worker/        # Async OCR/extraction pipeline
│   └── search-service/       # Search index management
├── frontend/                 # Next.js / React SPA
├── deploy/
│   └── helm/
│       └── easywork/         # Production Helm chart (see Helm section)
├── docs/
│   └── adr/                  # Architecture Decision Records (MADR format)
├── e2e/                      # Cross-stack Playwright tests
├── compose.yml               # Local dev only — not a production target
├── .claude/                  # Claude Code agents, hooks, settings
└── CLAUDE.md
```

> The backend sub-modules share a parent Maven POM. Each runs as an independent
> Spring Boot application. Do not couple them at compile time via shared JPA
> entities — use shared DTOs via a `common` module if needed.

---

## Tech stack

### Backend (`backend/`)
| Concern | Choice |
|---|---|
| Language | Java 21 (LTS) |
| Framework | Spring Boot 3.x (latest stable) |
| Build | Maven multi-module (generated via [start.spring.io](https://start.spring.io)) |
| DB (production) | PostgreSQL 16+ |
| DB (local / CI) | H2 (in-memory, `MODE=PostgreSQL`) |
| Auth | Spring Security + OAuth2 / OIDC (Keycloak-compatible) |
| RBAC / Audit | Spring Security `@PreAuthorize` + Spring Data Envers |
| API | REST (OpenAPI 3.1, contract-first with springdoc) |
| File storage | MinIO (S3-compatible) |
| Search | Meilisearch (default) / Elasticsearch (enterprise option) |
| Async messaging | RabbitMQ (default) / Kafka (high-throughput option) |
| Content extraction | Apache Tika |
| OCR | Tesseract via Tess4J |
| Archiving format | PDF/A (long-term preservation) |
| Observability | Spring Boot Actuator + Micrometer |
| Testing | JUnit 5, Mockito, Testcontainers |
| Native image | GraalVM (optional — for NAS / Raspberry Pi deployments) |

### Frontend (`frontend/`)
| Concern | Choice |
|---|---|
| Language | TypeScript (strict mode) |
| Framework | Next.js 15+ App Router (generated via `create-next-app`) |
| UI | shadcn/ui + Tailwind CSS |
| Typography | Fraunces (headings) / Inter (UI) / IBM Plex Mono (metadata) |
| State | TanStack Query (server state) + Zustand (client state) |
| Auth | NextAuth.js (delegates to backend OIDC) |
| Testing | Vitest (unit), Playwright (e2e) |

---

## Initialising the project

> **Do not hand-write scaffolding.** Always use the official generators, then layer
> configuration on top.

### Backend
```bash
# For each sub-module, generate via https://start.spring.io:
# - Project: Maven, Language: Java, Spring Boot: latest stable
# - Group: fr.classeur, Artifact: doc-service (or ingest-worker / search-service)
# - Common dependencies: Spring Web, Spring Data JPA, Spring Security,
#   Spring Validation, H2, PostgreSQL Driver, Flyway, Lombok,
#   springdoc-openapi, Spring Boot Actuator
# - doc-service adds: Spring Data Envers
# - ingest-worker adds: Spring AMQP (RabbitMQ), Apache Tika, Tess4J
# - search-service adds: Meilisearch Java client or Spring Data Elasticsearch
unzip <module>.zip -d backend/<module>/
```

### Frontend
```bash
cd frontend/
npx create-next-app@latest . \
  --typescript \
  --tailwind \
  --eslint \
  --app \
  --src-dir \
  --import-alias "@/*"
```

---

## Database

### Local development
- Driver: H2 — `jdbc:h2:mem:classeur;MODE=PostgreSQL;DB_CLOSE_DELAY=-1`
- Schema managed by **Flyway** (`src/main/resources/db/migration/`)
- Seed data via `import.sql` (H2 only, gitignored for sensitive data)

### Production
- PostgreSQL 16+
- Credentials injected via environment variables, **never hardcoded**
- Flyway runs on startup; scripts are version-controlled and **immutable after merge**

### Flyway rules
- Naming: `V{major}_{minor}__{description}.sql` (e.g. `V1_0__create_document_table.sql`)
- One logical change per migration
- Never modify or delete a migration that has been merged to `main`
- Destructive changes (drop column, rename) require a prior deprecation ADR

---

## Document lifecycle

Every document follows this state machine — no skipping states:

```
Received → Extracting → OCR → Classifying → Ready (Active)
                                                  │
                                             Archived
                                                  │
                                               Trash          ← always passes through Trash
                                                  │
                                        Permanent deletion
```

- Retention policies are **automatic** and configurable per document type
- Purge events are written to an **immutable audit log** (Spring Data Envers)
- GDPR right to erasure: real deletion from PostgreSQL, MinIO, and search index;
  only the audit entry survives (without personal data)
- Long-term archiving uses **PDF/A** format

---

## GDPR & compliance

- Right to erasure must delete from all three stores: DB, object store, search index
- Audit log of deletions is kept without personal data
- No personal data in logs (see logging rules below)
- Data minimisation: do not extract or store more metadata than strictly needed
- Add a GDPR impact note to ADRs that introduce new personal data fields

---

## Testing requirements

Tests are **mandatory** for every feature or code change. PRs without tests are blocked.

### Backend
| Layer | Tooling | Scope |
|---|---|---|
| Unit | JUnit 5 + Mockito | Services, domain logic, mappers |
| Integration | JUnit 5 + Testcontainers | Repositories, REST controllers (`@SpringBootTest`) |
| Contract | Spring REST Docs or Pact | API contracts consumed by frontend |

- Testcontainers spins up real Postgres, MinIO, and RabbitMQ — no mocking of
  infrastructure in integration tests
- Minimum coverage gate: **80 % line coverage** (JaCoCo enforced in CI)
- Tests live in `src/test/java/`, mirroring the main package structure

### Frontend
| Layer | Tooling | Scope |
|---|---|---|
| Unit | Vitest + Testing Library | Components, hooks, utils |
| Integration | Vitest | Page-level rendering with mocked API |

### End-to-end
- Location: `e2e/` at repo root
- Tooling: **Playwright** (TypeScript)
- Runs against a full Docker Compose stack
- Must cover every user-facing happy path and critical error path

---

## Code quality standards

### General
- Zero compiler warnings in both Java and TypeScript
- No `TODO`, `FIXME`, or `System.out.println` merged to `main`
- All public API surface must have OpenAPI annotations

### Java
- Follow [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- Checkstyle + SpotBugs + PMD run in CI
- Lombok is permitted; avoid Lombok on domain model classes
- Prefer `record` types for DTOs
- No raw types, no unchecked casts

### TypeScript
- `strict: true` in `tsconfig.json`
- ESLint with `@typescript-eslint/recommended` + `next/core-web-vitals`
- Prettier for formatting (single source of truth)
- No `any` — use `unknown` + type guards instead

---

## Security standards

This is an **enterprise-grade** application. Security is not optional.

- Authentication: OAuth2/OIDC only — no custom username/password endpoints without review
- Authorisation: `@PreAuthorize` at service layer; RBAC enforced, least-privilege principle
- Secrets: environment variables or Vault — never in source
- Dependencies: OWASP Dependency-Check in CI; CVSS ≥ 7 blocks merge
- HTTP headers: Spring Security configures HSTS, CSP, X-Frame-Options, etc.
- Input validation: Bean Validation (`@Valid`) on all controller inputs; re-validate in domain
- File uploads: validate MIME type server-side (Apache Tika); store in MinIO outside webroot;
  scan with ClamAV in async pipeline
- Logging: never log personal data or credentials; structured JSON (Logback + logstash-logback-encoder)
- CORS: explicit allowlist; wildcard `*` forbidden in production

---

## Agents

The following Claude Code agents are active for this project.

### `/security-review`
Scans diffs for OWASP Top 10, secrets exposure, missing auth guards, unsafe file
handling, and GDPR violations. Blocks merge on HIGH findings.

### `/code-review`
Reviews for correctness, style compliance, dead code, missing tests, and API
contract violations. Maps to the standards in this file.

### `/plan`
Produces an implementation plan before any non-trivial feature starts. Output must
be approved before coding begins.

### `/simplify`
Runs after implementation: removes duplication, unnecessary abstractions, and
altitude issues.

### `update-config`
Manages `.claude/settings.json`, hooks, and permissions. All automated behaviours
are configured here — not in memory.

---

## Development workflow

1. **Plan first** — run `/plan` for any task spanning more than 3 files.
2. **Branch** from `main` — `git checkout -b feat/<short-description>` or `fix/<short-description>`.
3. **Write tests first** for new behaviour (TDD preferred; tests must accompany code at minimum).
4. **Run quality checks locally** before pushing:
   ```bash
   # Backend
   cd backend && mvn verify

   # Frontend
   cd frontend && npm run lint && npm run type-check && npm run test

   # E2E (requires Docker)
   cd e2e && npx playwright test
   ```
5. **Open a PR** — title must follow Conventional Commits (`feat:`, `fix:`, `chore:`, etc.).
6. **Agents run automatically** — security-review, code-review, and simplify must all pass
   (or be explicitly dismissed with justification).
7. **Squash merge** to `main`.

---

## Environment variables

| Variable | Service | Description |
|---|---|---|
| `SPRING_DATASOURCE_URL` | all backend | JDBC URL (Postgres in prod) |
| `SPRING_DATASOURCE_USERNAME` | all backend | DB username |
| `SPRING_DATASOURCE_PASSWORD` | all backend | DB password |
| `SPRING_SECURITY_OAUTH2_*` | doc-service | OIDC issuer / client config |
| `MINIO_ENDPOINT` | doc-service, ingest-worker | MinIO URL |
| `MINIO_ACCESS_KEY` | doc-service, ingest-worker | MinIO access key |
| `MINIO_SECRET_KEY` | doc-service, ingest-worker | MinIO secret key |
| `MINIO_BUCKET` | doc-service, ingest-worker | Target bucket name |
| `MEILISEARCH_HOST` | search-service | Meilisearch URL |
| `MEILISEARCH_API_KEY` | search-service | Meilisearch master key |
| `SPRING_RABBITMQ_HOST` | ingest-worker | RabbitMQ host |
| `NEXTAUTH_SECRET` | frontend | NextAuth signing secret |
| `NEXTAUTH_URL` | frontend | Public URL of the frontend |
| `NEXT_PUBLIC_API_URL` | frontend | Base URL of the backend API |

Never commit `.env` files. Provide `.env.example` with placeholder values.

---

## Local development — Docker Compose

`compose.yml` at repo root is the **local-only** stack. It is not the production
deployment target — enterprises use the Helm chart (see below).

```yaml
# compose.yml (to be created during setup)
services:
  postgres:
    image: postgres:16-alpine
  minio:
    image: minio/minio:latest
    command: server /data --console-address ":9001"
  rabbitmq:
    image: rabbitmq:3-management-alpine
  meilisearch:
    image: getmeili/meilisearch:latest
  doc-service:
    build: ./backend/doc-service
    depends_on: [postgres, minio, rabbitmq]
  ingest-worker:
    build: ./backend/ingest-worker
    depends_on: [postgres, minio, rabbitmq]
  search-service:
    build: ./backend/search-service
    depends_on: [meilisearch, rabbitmq]
  frontend:
    build: ./frontend
    depends_on: [doc-service]
```

---

## Production deployment — Helm chart

Enterprise and SME deployments target **Kubernetes**. A first-party Helm chart
lives at `deploy/helm/easywork/`.

### Chart structure

```
deploy/
└── helm/
    └── easywork/
        ├── Chart.yaml
        ├── values.yaml              # defaults (all features on, reasonable sizing)
        ├── values-family.yaml       # overlay: single replica, minimal resources
        ├── values-sme.yaml          # overlay: HA, HPA, PodDisruptionBudget
        ├── templates/
        │   ├── doc-service/         # Deployment, Service, HPA, PDB
        │   ├── ingest-worker/       # Deployment, HPA (CPU-driven, OCR-heavy)
        │   ├── search-service/      # Deployment, Service
        │   ├── frontend/            # Deployment, Service, Ingress
        │   ├── ingress.yaml         # Single Ingress for the full stack
        │   ├── serviceaccount.yaml
        │   ├── rbac.yaml
        │   └── _helpers.tpl
        └── charts/                  # Sub-chart dependencies (bitnami)
```

### External dependencies (sub-charts from Bitnami)

The chart uses official Bitnami sub-charts for stateful services. Never run
databases or message brokers as raw Deployments.

| Service | Sub-chart | Notes |
|---|---|---|
| PostgreSQL | `bitnami/postgresql` | Primary + read replica in SME values |
| MinIO | `bitnami/minio` | Distributed mode in SME values |
| RabbitMQ | `bitnami/rabbitmq` | Clustered in SME values |
| Meilisearch | `meilisearch/meilisearch` | Single instance; swap for ES in enterprise |

### Key design rules for the chart

- All secrets are injected via `secretKeyRef` — never `value:` for credentials
- Use **external-secrets-operator** annotation pattern when a Vault/AWS SM backend
  is configured; fall back to Kubernetes Secrets otherwise
- `resources.requests` and `resources.limits` must be set on every container
- `readinessProbe` and `livenessProbe` must be configured for every application container
  (use the Spring Boot Actuator `/actuator/health` endpoint)
- `PodDisruptionBudget` required for all stateful-adjacent services in SME profile
- `HorizontalPodAutoscaler` on `ingest-worker` (CPU metric — OCR is CPU-bound)
- `NetworkPolicy` denies all ingress/egress by default; only open required ports
- Images must be pinned to a digest or an immutable tag — never `latest`
- Ingress uses `cert-manager` for TLS (`ClusterIssuer: letsencrypt-prod`)

### Installing locally (kind / minikube)

```bash
helm dependency update deploy/helm/easywork
helm install easywork deploy/helm/easywork \
  --namespace easywork --create-namespace \
  -f deploy/helm/easywork/values-family.yaml \
  --set global.postgresql.auth.password=dev
```

### CI/CD

- `helm lint` and `helm template | kubeval` run in CI on every PR that touches `deploy/`
- Chart version bumps follow SemVer and are decoupled from application version
- `ct` (chart-testing) runs an install + test cycle in the CI cluster

---

## Decisions log

Significant architectural decisions are recorded as **ADR** files in `docs/adr/`
using the [MADR](https://adr.github.io/madr/) template. Create an ADR before
opening a PR that introduces a meaningful technical choice or changes a prior decision.

Existing decisions (from pre-project exploration):
- Java / Spring Boot chosen over Python, Node, .NET, Go, Rust — OCR ecosystem,
  RBAC/audit maturity, Spring Data Envers
- Meilisearch as default search (simpler ops); Elasticsearch as enterprise option
- RabbitMQ as default queue; Kafka for high-throughput deployments
- GraalVM native image deferred — only if NAS/Raspberry Pi target is confirmed
- Nextcloud integration: bidirectional metadata sync, optional connector model
- Retention: automatic per document type, always through Trash, immutable audit log

---

## What Claude should never do

- Hardcode credentials, tokens, or secrets in any file
- Bypass Spring Security filter chains or disable CSRF without explicit instruction
- Use `@SuppressWarnings` or ESLint `disable` comments without a justification comment
- Write Flyway migrations that modify or drop columns without a prior deprecation ADR
- Let test coverage fall below the 80 % gate
- Skip `/plan` for features spanning more than 3 files
- Use `any` in TypeScript or raw types in Java
- Delete from only one store (DB, MinIO, or search index) without deleting from all three
- Log personal data, document content, or credentials
- Store files in PostgreSQL — binary content always goes to MinIO
