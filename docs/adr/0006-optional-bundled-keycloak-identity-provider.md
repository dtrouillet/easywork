# 0006 — Add an optional, disabled-by-default bundled Keycloak sub-chart

**Status:** Accepted

**Date:** 2026-08-04

## Context

The Helm chart added in ADR 0005 treats OIDC as strictly bring-your-own:
`oauth2.jwtIssuerUri`, `cors.allowedOrigins`, `app.nextAuthSecret`,
`app.keycloakClientSecret`, and `app.keycloakIssuer` are all `required()` with
no default, so every install — including a first local smoke test — needs an
already-running Keycloak/IdP reachable from the cluster. A manual `helm
install` test on a real `kind`-style cluster confirmed this directly: without
a real IdP, the only way to get past `helm install`'s `required()` guards was
to pass fake placeholder OIDC values, after which `doc-service`,
`ingest-worker`, and `search-service` crash-looped on Spring Security's OIDC
issuer resolution at startup — a working chart install, but not a working
application.

`compose.yml` already runs a real, working Keycloak for local dev
(`start-dev --import-realm`, importing `keycloak/realm-easywork.json`), but
that setup is explicitly local-only and cannot be reused as-is: it hardcodes
`KEYCLOAK_ADMIN_PASSWORD: admin`, a client secret (`"secret":
"easywork-secret"`), and a seeded `dev`/`dev` user, all committed to git.
CLAUDE.md's "never hardcode credentials, tokens, or secrets in any file" rule
applies to the production-facing Helm chart even where it doesn't strictly
apply to the dev-only compose stack — copying `realm-easywork.json` verbatim
into the chart would ship a real, guessable credential in every clone of this
repository.

Family/self-host users are the ones most affected: CLAUDE.md's dual-target
design promises "zero config" for the family tier, but every other bundled
dependency (Postgres/RabbitMQ/MinIO/Meilisearch) already gets a one-command
working instance via `values-family.yaml`, while OIDC alone required standing
up external infrastructure first. SME/enterprise deployments are the opposite
case: CLAUDE.md's stack table already frames auth as "OAuth2/OIDC
(Keycloak-compatible)", not "Keycloak bundled" — those deployments already run
their own IdP (Keycloak, Entra ID, Okta, Auth0) and must keep using it
unchanged.

## Decision drivers

- CLAUDE.md's explicit ban on hardcoding credentials/secrets in any committed
  file — rules out reusing `realm-easywork.json`'s hardcoded admin
  password/client secret/seeded user as-is
- ADR 0005's precedent: CloudPirates OCI charts, condition-gated
  (`<dep>.enabled`), pinned to a real image digest, resources/probes/PDB set
  explicitly since CloudPirates ships no defaults
- Must not change the SME/enterprise external-IdP path at all — every
  `required()` failure mode on `keycloak.enabled: false` must stay
  byte-identical to today
- Family tier's zero-config goal: a single `helm install -f
  values-family.yaml` should produce a fully working, logged-in-capable
  system with no external IdP to stand up first
- A generated OIDC client secret must reach both our own `app` Secret and the
  Keycloak realm-import Secret identically — Helm's `lookup`/`randAlphaNum`
  are not memoized across separate template files within one render, which
  constrains where this can be computed (see `templates/secret.yaml`)

## Considered options

- **Option A (chosen)** — Add `keycloak` as a fourth condition-gated
  CloudPirates dependency (`oci://registry-1.docker.io/cloudpirates/keycloak`),
  disabled by default, with its own dedicated Ingress/hostname and its own
  dedicated, isolated embedded Postgres instance. `values-family.yaml` sets
  `keycloak.enabled: true`; `values-sme.yaml` and the chart-wide default stay
  `false`.
- **Option B (rejected)** — Same bundled chart, but served under a sub-path of
  the umbrella chart's existing single Ingress (`/auth`) instead of its own
  hostname, via `keycloak.keycloak.httpRelativePath`. Would avoid a second DNS
  record, but Keycloak-under-a-subpath is a fragile, non-default upstream
  configuration (relative asset URLs, `.well-known` discovery, admin console
  redirects, SAML endpoints all become subpath-relative) and would couple
  `templates/ingress.yaml` forever to Keycloak-specific `httpRelativePath`/
  `hostnameStrict`/`proxyHeaders` settings — a maintenance coupling this chart
  doesn't have anywhere else. A broken OIDC discovery document also fails
  opaquely deep inside NextAuth/Spring Security token validation, a much worse
  failure mode than "provision one more DNS record."
- **Option C (rejected)** — Hand-roll a first-party Keycloak
  `StatefulSet`/`Deployment` instead of depending on a third-party chart.
  Rejected for the same reason ADR 0005 rejected hand-rolling Postgres/
  RabbitMQ/MinIO: reimplements a large amount of well-trodden logic (realm
  import, DB wiring, proxy-header handling, TLS) a maintained chart already
  solves.
- **Option D (rejected)** — No bundled option at all; keep OIDC strictly
  bring-your-own for every tier. Simplest change, but leaves the family-tier
  zero-config gap unresolved and doesn't fix the crash-loop-on-fake-values
  problem surfaced during manual testing.

## Decision outcome

**Chosen option:** A. `Chart.yaml` gains a fourth dependency, `keycloak`
0.21.19 from CloudPirates, condition-gated on `keycloak.enabled` (default
`false`), image pinned to a real digest
(`keycloak/keycloak:26.7.0@sha256:...`). Its bundled `postgres` sub-sub-chart
stays enabled by default — a fully isolated, dedicated database instance,
deliberately never sharing the umbrella chart's own `postgresql:` release
(that CloudPirates postgres chart doesn't support multiple databases on one
release cleanly, per ADR 0005's own negative-consequences section).

Three credentials are generated, never hardcoded, and kept stable across
`helm upgrade` via the same `lookup`-and-reuse-on-upgrade pattern ADR 0005
introduced for RabbitMQ's erlang cookie: the Keycloak admin password, the
embedded Postgres password, and the realm-import client secret. The client
secret specifically must land identically in two Kubernetes objects (our own
`app` Secret's `keycloak-client-secret` key, already consumed by
`frontend/deployment.yaml`, and the realm-import Secret's client definition)
— since `lookup`/`randAlphaNum` aren't memoized across separate template
files, both objects are now rendered from the same `templates/secret.yaml`
file, sharing one Go-template variable, rather than as a separate file. The
realm JSON itself is built as a Helm `dict`/`list` piped through `toJson`
(never hand-interpolated), reusing `keycloak/realm-easywork.json`'s non-secret
settings (`sslRequired`, `bruteForceProtected`, default/optional client
scopes) but with **no seeded `users` array** — NOTES.txt instead instructs
creating the first real user via the Admin Console/API after install.

Two new helpers in `_helpers.tpl` (`easywork.oauthIssuerUri`,
`easywork.oauthJwkSetUri`) toggle between the bundled instance and the
existing `required()` external-IdP values, following the exact same
`easywork.minioEndpoint`-style pattern already used for every other optional
dependency; `easywork.backendCommonEnv` and `frontend/deployment.yaml` were
updated to call them instead of reading `.Values.oauth2`/`.Values.app.
keycloakIssuer` directly. `templates/networkpolicy.yaml` gained explicit
egress allow rules (frontend and all three backend components → the bundled
Keycloak pod selector) — without them, `keycloak.enabled: true` combined with
`networkPolicy.enabled: true` (which family tier now exercises) would drop
login and JWK-fetch traffic silently.

Verified with `helm dependency update`/`lint`/`template` for both
`values-family.yaml` (now exercising `keycloak.enabled: true`) and
`values-sme.yaml`/default (unchanged, `false`). A real `kind` cluster install
with `values-family.yaml`, following ADR 0005's own verification bar, is the
acceptance test for this feature: confirms the embedded Postgres
host/credential wiring resolves correctly (a nested-subchart-of-subchart
naming detail `helm template` alone can't fully confirm), the realm/client
import actually succeeds, the three backend pods stop crash-looping on OIDC
issuer resolution, and a real browser login round-trip works end-to-end once
a first user is created via the Admin Console.

### Positive consequences

- Family/self-host users get a genuinely one-command working login, closing
  the exact gap a manual `helm install` smoke test surfaced
- SME/enterprise external-IdP path is untouched — every `required()` failure
  mode is byte-identical when `keycloak.enabled: false`
- No credential is ever hardcoded in a committed file, unlike
  `compose.yml`/`realm-easywork.json`'s dev-only shortcuts
- Same CloudPirates/condition-gated/pinned-digest conventions as every other
  dependency — no new pattern introduced for future maintainers to learn

### Negative consequences / risks

- **The frontend's single-issuer design is a real operational prerequisite,
  not a chart bug.** `frontend/src/lib/auth.ts` uses one
  `AUTH_KEYCLOAK_ISSUER` value for both browser redirects and a direct
  server-side `fetch()` for token refresh — there is no split between a
  "browser URL" and a "pod-reachable URL" the way the backend's
  `jwtIssuerUri`/`jwtJwkSetUri` already are. Once bundled, the frontend pod
  itself must be able to reach Keycloak's *public* hostname from inside the
  cluster, which needs hairpin-NAT/LoadBalancer support or split-horizon DNS.
  Documented prominently in `values.yaml`, `NOTES.txt`, and here; not solved
  inside this chart.
- A second DNS record and TLS host to provision and manage (mitigated:
  cert-manager automates the certificate itself once the hostname exists).
- One more isolated, single-instance Postgres with the same non-HA ceiling
  ADR 0005 already accepted for the main `postgresql` dependency — no new risk
  class, just one more instance of an already-accepted one.
- CloudPirates' `keycloak` chart is newer and less proven than their
  `postgres`/`rabbitmq`/`minio` charts (same "smaller community project"
  caveat ADR 0005 already accepted, now extended to a fourth chart) — its
  dependency version and default image digest must be re-verified before each
  bump, same as the other three.

**GDPR impact note:** Keycloak's embedded Postgres instance becomes a **new**
personal-data store (usernames, emails, credential hashes) when
`keycloak.enabled: true`, not covered by ADR 0001's existing
DB/MinIO/search-index erasure scope. Keycloak has its own Admin REST API for
user deletion, but it is not yet wired into this application's existing
GDPR-erasure workflow — tracked as follow-up application-level work, not
solved by this ADR.

## Pros and cons of the options

### Option A: dedicated Ingress + dedicated hostname (chosen)

**Pros:** zero new Ingress template code (Keycloak's own chart already ships a
fully-supported `ingress`/`tls`/cert-manager block); no coupling between our
`templates/ingress.yaml` and Keycloak-specific proxy/hostname requirements;
matches how every other dependency in this chart is already treated as fully
self-contained.

**Cons:** one more DNS record and TLS host for the operator to provision.

### Option B: shared Ingress, sub-path

**Pros:** a single hostname/TLS cert for the whole stack.

**Cons:** Keycloak-under-a-subpath is fragile and non-default upstream
(relative URLs, `.well-known` discovery, admin console, SAML endpoints); a
broken OIDC discovery document fails opaquely inside NextAuth/Spring Security
rather than at `helm install` time; permanently couples
`templates/ingress.yaml` to Keycloak's own reverse-proxy assumptions. Rejected.

### Option C: hand-rolled Keycloak Deployment

**Pros:** total control, no third-party chart risk.

**Cons:** reimplements realm-import, DB wiring, proxy-header handling, and TLS
logic a maintained chart already solves — same reasoning ADR 0005 used to
reject hand-rolling Postgres/RabbitMQ/MinIO. Rejected.

### Option D: no bundled option, BYO IdP for every tier

**Pros:** simplest possible diff; zero new attack surface/dependency.

**Cons:** leaves the family-tier zero-config promise unmet and does not fix
the crash-loop-on-fake-values failure mode a real install already surfaced.
Rejected.

## Links

- Chart: `deploy/helm/easywork/Chart.yaml`
- Values: `deploy/helm/easywork/values.yaml`,
  `deploy/helm/easywork/values-family.yaml`
- Templates: `deploy/helm/easywork/templates/secret.yaml`,
  `deploy/helm/easywork/templates/external-secret.yaml`,
  `deploy/helm/easywork/templates/_helpers.tpl`,
  `deploy/helm/easywork/templates/networkpolicy.yaml`,
  `deploy/helm/easywork/templates/frontend/deployment.yaml`,
  `deploy/helm/easywork/templates/NOTES.txt`
- Related code: `frontend/src/lib/auth.ts` (single-issuer design driving the
  hairpin-NAT/DNS operational prerequisite above), `compose.yml`,
  `keycloak/realm-easywork.json` (dev-only reference for realm shape, not
  reused verbatim)
- Related: [0005](0005-helm-chart-third-party-dependency-charts.md) —
  CloudPirates dependency precedent, embedded-Postgres-can't-share-a-release
  finding this ADR reuses for Keycloak's own database
- Related: [0001](0001-document-audit-scope-and-gdpr-erasure.md) — existing
  GDPR erasure scope (DB/MinIO/search-index), which does not yet cover
  Keycloak's own user store when bundled
