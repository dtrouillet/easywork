# CLAUDE.md — easywork

## Project overview

**easywork** is an enterprise-grade document management system inspired by [paperless-ngx](https://docs.paperless-ngx.com/). It centralises, OCRs, indexes and serves digital documents with a clean, modern UI. The project targets production deployments in corporate environments and must satisfy security, compliance, and reliability standards accordingly.

---

## Repository structure

This is a **monorepo**. Never collapse the two applications into one module.

```
easywork/
├── backend/          # Java / Spring Boot API
├── frontend/         # Next.js / React UI
├── docs/             # Architecture decisions, API specs, runbooks
├── e2e/              # Cross-stack end-to-end tests (Playwright)
├── .claude/          # Claude Code configuration (agents, hooks, settings)
└── CLAUDE.md
```

---

## Tech stack

### Backend (`backend/`)
| Concern | Choice |
|---|---|
| Language | Java 21 (LTS) |
| Framework | Spring Boot 3.x (latest stable) |
| Build | Maven (generated via [start.spring.io](https://start.spring.io)) |
| DB (production) | PostgreSQL 16+ |
| DB (local / CI) | H2 (in-memory, compatibility mode `MODE=PostgreSQL`) |
| Auth | Spring Security + OAuth2 / OIDC (Keycloak-compatible) |
| API | REST (OpenAPI 3.1, contract-first with springdoc) |
| Messaging | Spring Events (internal); RabbitMQ optional for async OCR |
| Testing | JUnit 5, Mockito, Testcontainers (Postgres for integration) |

### Frontend (`frontend/`)
| Concern | Choice |
|---|---|
| Language | TypeScript (strict mode) |
| Framework | Next.js 15+ App Router (generated via `create-next-app`) |
| UI | shadcn/ui + Tailwind CSS |
| State | Zustand or React Query (TanStack Query) |
| Auth | NextAuth.js (delegates to backend OIDC) |
| Testing | Vitest (unit), Playwright (e2e) |

---

## Initialising the project

> **Do not hand-write scaffolding.** Always use the official generators, then layer configuration on top.

### Backend
```bash
# Visit https://start.spring.io and download with:
# - Project: Maven, Language: Java, Spring Boot: latest stable
# - Group: fr.easywork, Artifact: backend
# - Dependencies: Spring Web, Spring Data JPA, Spring Security,
#   Spring Validation, H2, PostgreSQL Driver, Flyway, Lombok,
#   springdoc-openapi, Spring Boot Actuator
unzip backend.zip -d backend/
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
- Driver: H2 with `spring.datasource.url=jdbc:h2:mem:easywork;MODE=PostgreSQL;DB_CLOSE_DELAY=-1`
- Schema managed by **Flyway** (`src/main/resources/db/migration/`)
- Seed data via `import.sql` (H2 only, gitignored for sensitive data)

### Production
- PostgreSQL 16+
- Credentials injected via environment variables, **never hardcoded**
- Flyway runs migrations on startup; migration scripts are version-controlled and **never modified after merge**

### Flyway rules
- Script naming: `V{major}_{minor}__{description}.sql` (e.g. `V1_0__create_document_table.sql`)
- One logical change per migration
- Migrations are **immutable** once merged to `main`

---

## Testing requirements

Tests are **mandatory** for every feature or code change. PRs that omit tests are blocked.

### Backend
| Layer | Tooling | Scope |
|---|---|---|
| Unit | JUnit 5 + Mockito | Services, domain logic, mappers |
| Integration | JUnit 5 + Testcontainers (Postgres) | Repositories, REST controllers (`@SpringBootTest`) |
| Contract | Spring REST Docs or Pact | API contracts exposed to frontend |

- Minimum coverage gate: **80 % line coverage** (enforced by JaCoCo in CI)
- Tests live in `src/test/java/`, mirroring the main package structure

### Frontend
| Layer | Tooling | Scope |
|---|---|---|
| Unit | Vitest + Testing Library | Components, hooks, utils |
| Integration | Vitest | Page-level rendering with mocked API |

### End-to-end
- Location: `e2e/` at repo root
- Tooling: **Playwright** (TypeScript)
- Runs against a Docker Compose stack (backend + frontend + Postgres)
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
- Authorisation: method-level `@PreAuthorize` / role-based; least-privilege principle
- Secrets: environment variables or a secrets manager (Vault) — never in source
- Dependencies: OWASP Dependency-Check runs in CI; CVSS ≥ 7 blocks merge
- HTTP headers: Spring Security configures HSTS, CSP, X-Frame-Options, etc.
- Input validation: Bean Validation (`@Valid`) on all controller inputs; validate again in domain
- File uploads: validate MIME type server-side; store outside webroot; scan with ClamAV in async pipeline
- Logging: never log personal data or credentials; use structured JSON logs (Logback + logstash-logback-encoder)
- CORS: explicit allowlist; wildcard `*` is forbidden in production

---

## Agents

The following Claude Code agents are active for this project. Each agent runs in its own worktree and reports before any change is merged.

### `/security-review`
Scans diffs for OWASP Top 10, secrets exposure, missing auth guards, and unsafe file handling. Blocks merge on HIGH findings.

### `/code-review`  
Reviews for correctness, code style compliance, dead code, and missing tests. Maps to the standards in this file.

### `/plan`
Produces an implementation plan before any non-trivial feature starts. Output must be approved before coding begins.

### `/simplify`
Runs after implementation: removes duplication, unnecessary abstractions, and altitude issues.

### `update-config`
Manages `.claude/settings.json`, hooks, and permissions. All automated behaviours (pre-commit checks, on-save linting) are configured here — not in memory.

---

## Development workflow

1. **Plan first** — run `/plan` for any task requiring more than a handful of file changes.
2. **Branch** from `main` — `git checkout -b feat/<short-description>` or `fix/<short-description>`.
3. **Write tests first** for new behaviour (TDD preferred, at minimum tests must accompany code).
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
6. **Agents run automatically** — security-review, code-review, and simplify must all pass (or be explicitly dismissed with justification).
7. **Squash merge** to `main`.

---

## Environment variables

| Variable | Used by | Description |
|---|---|---|
| `SPRING_DATASOURCE_URL` | backend | JDBC URL (Postgres in prod) |
| `SPRING_DATASOURCE_USERNAME` | backend | DB username |
| `SPRING_DATASOURCE_PASSWORD` | backend | DB password |
| `SPRING_SECURITY_OAUTH2_*` | backend | OIDC issuer / client config |
| `NEXTAUTH_SECRET` | frontend | NextAuth signing secret |
| `NEXTAUTH_URL` | frontend | Public URL of the frontend |
| `NEXT_PUBLIC_API_URL` | frontend | Base URL of the backend API |

Never commit `.env` files. Provide `.env.example` with placeholder values.

---

## Docker Compose (local full-stack)

A `compose.yml` at repo root starts the full stack:

```yaml
# compose.yml (to be created during setup)
services:
  postgres:
    image: postgres:16-alpine
  backend:
    build: ./backend
    depends_on: [postgres]
  frontend:
    build: ./frontend
    depends_on: [backend]
```

---

## Decisions log

Significant architectural decisions are recorded as **ADR** files in `docs/adr/` using the [MADR](https://adr.github.io/madr/) template. When you introduce a meaningful technical choice, create an ADR before opening the PR.

---

## What Claude should never do

- Hardcode credentials, tokens, or secrets in any file
- Bypass Spring Security filter chains or disable CSRF without explicit instruction
- Use `@SuppressWarnings` or ESLint `disable` comments without a justification comment
- Write Flyway migrations that modify or drop existing columns without a deprecation ADR
- Merge test coverage below 80 % gate
- Skip the `/plan` step for features spanning more than 3 files
- Use `any` in TypeScript or raw types in Java
