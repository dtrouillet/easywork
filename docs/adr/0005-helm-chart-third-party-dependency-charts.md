# 0005 — Add the production Helm chart, sourcing Postgres/RabbitMQ/MinIO from CloudPirates instead of Bitnami

**Status:** Accepted

**Date:** 2026-08-03

## Context

CLAUDE.md mandates a first-party Helm chart at `deploy/helm/easywork/` as the
production deployment target (`compose.yml` is explicitly local-only), with
`doc-service`/`ingest-worker`/`search-service`/`frontend` as independent
Deployments and Postgres/MinIO/RabbitMQ/Meilisearch as Bitnami sub-charts. No
chart existed yet (`deploy/` didn't exist — noted as a gap in ADR 0004).

While building the chart, live research into the pinned Bitnami sub-charts
found the ecosystem had moved since CLAUDE.md was written: Bitnami's free
catalog is now OCI-only, and `bitnami/rabbitmq`/`bitnami/minio` default to
images that only exist in a frozen `bitnamilegacy` namespace — no further CVE
patches. Worse, MinIO Inc. itself archived the entire open-source `minio/minio`
and `minio/operator` projects; there is no actively-developed upstream for
object storage to point at at all anymore, Bitnami or otherwise. Shipping a
chart that pins to abandoned, unpatched images would contradict CLAUDE.md's own
security standard (OWASP Dependency-Check, CVSS ≥ 7 blocks merge) on day one.

This was surfaced to the user via `AskUserQuestion` before proceeding; the user
chose to move off Bitnami for postgres/rabbitmq/minio in favor of an actively
maintained alternative, rather than accept the legacy-image risk.

## Decision drivers

- CLAUDE.md's explicit rule: "Never run databases or message brokers as raw
  Deployments" — third-party dependencies must be real, maintained sub-charts
- Images must be pinned to a digest and receive ongoing security patches
  (CLAUDE.md security standards; "never `latest`" chart rule)
- Minimise divergence from CLAUDE.md's mandated structure and market-standard
  chart conventions (per-tier values overlays, `existingSecret` support,
  condition-gated dependencies)
- Heavy customizability via values — every component individually
  configurable, with an escape hatch to an externally-managed instance
- No single store should become a hard blocker to shipping a working,
  installable chart today

## Considered options

- **Option A (chosen)** — Use CloudPirates (`CloudPirates-io/helm-charts`,
  `oci://registry-1.docker.io/cloudpirates`) for `postgres`/`rabbitmq`/`minio`;
  keep the official `meilisearch/meilisearch-kubernetes` chart for Meilisearch
  (unaffected by the Bitnami/MinIO situation).
- **Option B (rejected)** — Keep Bitnami as CLAUDE.md originally specified,
  accepting `bitnamilegacy` images for rabbitmq/minio and no image-tag
  versioning at all for postgresql.
- **Option C (rejected)** — Hand-roll first-party `StatefulSet` templates for
  postgres/rabbitmq/minio instead of depending on any third-party chart.
- **Option D (rejected for now)** — Adopt real Kubernetes operators
  (CloudNativePG for Postgres, the RabbitMQ Cluster Operator, a MinIO-compatible
  object-store operator) instead of Helm sub-charts.

## Decision outcome

**Chosen option:** A — CloudPirates for postgres/rabbitmq/minio, official
Meilisearch chart unchanged. `Chart.yaml` pins `postgres` 0.19.12, `rabbitmq`
0.21.12, `minio` 0.13.1, `meilisearch` 0.36.0, all condition-gated
(`<dep>.enabled`) with a matching `external<Dep>` values block as an escape
hatch to point at an externally-managed instance instead. Images are pinned to
real digests (`postgres:18.4@sha256:...`, `rabbitmq:4.3.4-management@sha256:...`,
a digest-pinned rebuild of MinIO's last pre-archival release,
`cloudpirates/image-minio:RELEASE.2025-10-15...@sha256:...`). Each subchart's
credentials are read from the same per-domain Kubernetes Secret (or
`ExternalSecret`, gated by `externalSecrets.enabled`) that our own Deployments'
`secretKeyRef`s also reference (`templates/secret.yaml`, `_helpers.tpl`'s
`easywork.secretName`) — one credential, one Secret, no duplication.
`doc-service`/`ingest-worker`/`search-service`/`frontend` are each an
independent Deployment+Service+HPA+PDB, wired via `values-family.yaml`
(single replica, no HPA/PDB) and `values-sme.yaml` (HPA on ingest-worker,
PDB everywhere, RabbitMQ quorum queues at 3 replicas) tier overlays, and a
default-deny `NetworkPolicy` per component with explicit allow rules
(`templates/networkpolicy.yaml`).

Verified with `helm dependency update`/`lint`/`template` for both tiers, a
real `frontend` Docker build, and a genuine `kind` cluster install (`helm
install`, not `--dry-run`) where all four third-party dependencies and all
four of our own components (images loaded via `kind load docker-image`)
reached `Running`/`Ready` with real health checks passing. That real install
caught three bugs `helm template` alone could not have: a UID/GID mismatch
between the Dockerfile-created user and the chart's `runAsUser: 1000` pod
security context, the RabbitMQ erlang cookie regenerating on every `helm
upgrade` (now fixed via a `lookup`-and-reuse pattern in `templates/secret.yaml`
plus `helm.sh/resource-policy: keep`), and a Next.js App Router quirk that
silently dropped a manually-placed `<script>` tag (fixed by using
`next/script`'s `beforeInteractive` strategy in `frontend/src/app/layout.tsx`).

### Positive consequences

- No unpatched, frozen-namespace images anywhere in the chart's dependency
  graph; every image is pinned to a real digest of an official upstream
- Same `existingSecret`/values-driven customization pattern CLAUDE.md expects
  from Bitnami is preserved — CloudPirates charts follow the same conventions
- `external<Dep>` escape hatches mean any environment that already runs its
  own managed Postgres/RabbitMQ/object storage/Meilisearch can skip the
  bundled sub-chart entirely without touching templates
- Frontend's runtime `window.__ENV__` injection (`frontend/docker-entrypoint.sh`,
  `frontend/src/lib/api/client.ts`) lets a single built image be redeployed
  across environments/ingress hostnames purely via `frontend.env.apiUrl`,
  matching the backend Deployments' own env-var-driven configuration model
- A real `kind` install (not just template rendering) found and fixed bugs
  that would otherwise have surfaced on someone's first real deployment

### Negative consequences / risks

- **MinIO has no actively-developed upstream at all anymore.** The chart's
  `minio` image is pinned to CloudPirates' rebuild of MinIO's *last
  pre-archival* release — there is no expectation of future feature or CVE
  fixes from any upstream. There is also no distributed/erasure-coded
  multi-node HA mode in this chart; durability comes entirely from the
  underlying `StorageClass` (Longhorn, Ceph RBD, cloud block storage), not
  from MinIO-level replication.
  - Mitigation: called out prominently in `values.yaml`'s dependency-block
    comment, `minio.persistence.storageClass`'s comment, `values-sme.yaml`,
    and `templates/NOTES.txt` on every install/upgrade, plus here. Anyone
    running this in production must plan object-storage durability at the
    storage-class layer and monitor for a viable replacement (e.g. Garage,
    SeaweedFS, or a cloud-native object store) rather than assume MinIO will
    receive further updates.
- CloudPirates is a smaller, newer community project than Bitnami was — less
  battle-tested, and its dependency versions must be re-verified
  (`helm show chart oci://registry-1.docker.io/cloudpirates/<name>`) before
  each bump rather than trusted blindly, since it ships new chart versions
  frequently.
- The CloudPirates `postgres` chart's `replication.enabled` models exactly one
  primary + one streaming standby, and the standby must be a **separate**
  `helm install` (`replication.primary.host` pointed at the primary's
  Service) — it cannot be expressed as a single `postgresql:` values block
  within this umbrella chart's one release. Bumping `postgresql.replicaCount`
  alone would not give replication (each StatefulSet replica gets an
  independent, unsynced PVC — silent data divergence, not HA).
  - Mitigation: documented as a manual operational step directly in
    `values-sme.yaml` with the exact second `helm install` command; anyone
    who needs automatic failover is pointed at evaluating CloudNativePG (a
    real operator) instead of this dependency.
- `search-service`'s only input, `DocumentReadyEvent`, is still in-process-only
  delivery (ADR 0004's known gap) — deploying it as a genuinely separate pod
  via this chart means search indexing silently never happens until that's
  fixed upstream in the application. The chart still supports deploying it
  separately (per CLAUDE.md's per-profile-pod design), with the gap called
  out in `values.yaml` and rendered into `NOTES.txt`.
  - Mitigation: none in this chart — tracked as application-level follow-up
    work under ADR 0004, not a Helm concern.

**GDPR impact note:** not applicable. This ADR only changes deployment
topology and third-party image sourcing; it introduces no new personal-data
fields, storage location, or processing path beyond what ADR 0001 already
covers for Postgres/MinIO/search-index erasure.

## Pros and cons of the options

### Option A: CloudPirates for postgres/rabbitmq/minio (chosen)

**Pros:** actively maintained, real official upstream images with digest
pinning, `existingSecret` support matching the Bitnami convention CLAUDE.md
originally assumed, weekly Renovate-driven digest bumps, minimal chart
structure churn versus what CLAUDE.md already specified.

**Cons:** smaller/newer community project than Bitnami; MinIO specifically is
still built on an archived upstream project regardless of which packager
republishes it — CloudPirates fixes the "frozen namespace" problem, not the
"no upstream" problem.

### Option B: Keep Bitnami as originally specified

**Pros:** zero deviation from CLAUDE.md's original text; Bitnami is a familiar,
long-established name.

**Cons:** ships unpatched `bitnamilegacy` images for rabbitmq/minio with no
forward CVE-fix path, and no versioned postgresql image tags at all —
directly contradicts CLAUDE.md's own "images must be pinned to a digest or
immutable tag" and OWASP Dependency-Check rules on day one. Rejected.

### Option C: Hand-rolled first-party StatefulSets

**Pros:** total control, no third-party chart risk at all.

**Cons:** directly contradicts CLAUDE.md's explicit "never run databases or
message brokers as raw Deployments" rule; reimplements a large amount of
well-trodden StatefulSet/PVC/readiness logic that a maintained chart already
solves. Rejected as out of scope for this change.

### Option D: Adopt real operators (CloudNativePG, RabbitMQ Cluster Operator)

**Pros:** genuine automatic failover and a maintained, purpose-built control
loop instead of a Helm sub-chart's more limited HA model — directly solves the
postgres replication ceiling noted above.

**Cons:** a cluster-wide operator install is a much larger scope change than
this ADR (separate CRDs, RBAC, and lifecycle outside this chart's own release);
deferred rather than bundled into the first working version of the chart.
Noted as the natural next step if automatic Postgres failover becomes a hard
requirement.

## Links

- Chart: `deploy/helm/easywork/Chart.yaml`
- Values: `deploy/helm/easywork/values.yaml`,
  `deploy/helm/easywork/values-family.yaml`,
  `deploy/helm/easywork/values-sme.yaml`
- Templates: `deploy/helm/easywork/templates/secret.yaml`,
  `deploy/helm/easywork/templates/external-secret.yaml`,
  `deploy/helm/easywork/templates/networkpolicy.yaml`,
  `deploy/helm/easywork/templates/_helpers.tpl`,
  `deploy/helm/easywork/templates/NOTES.txt`
- Related code: `frontend/Dockerfile`, `frontend/docker-entrypoint.sh`,
  `frontend/src/app/layout.tsx`, `frontend/src/lib/api/client.ts`,
  `frontend/src/app/api/health/route.ts`
- Related: [0001](0001-document-audit-scope-and-gdpr-erasure.md) — GDPR
  erasure across DB/object store/search index, unaffected by this change
- Related: [0004](0004-cross-pod-event-delivery-via-amqp.md) — noted "there is
  no Helm chart yet" and the `DocumentReadyEvent`/search-service cross-pod gap
  this chart's `NOTES.txt` now surfaces at install/upgrade time
- CLAUDE.md — Production deployment / Helm chart section (chart structure,
  Bitnami sub-chart mandate now superseded for postgres/rabbitmq/minio only,
  key design rules for secrets/probes/resources/NetworkPolicy/digest pinning)
