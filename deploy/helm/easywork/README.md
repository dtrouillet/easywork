# easywork

![Version: 0.1.0](https://img.shields.io/badge/Version-0.1.0-informational?style=flat-square) ![Type: application](https://img.shields.io/badge/Type-application-informational?style=flat-square) ![AppVersion: v0.0.1](https://img.shields.io/badge/AppVersion-v0.0.1-informational?style=flat-square)

easywork — enterprise document management system (DMS). Deploys doc-service, ingest-worker, search-service and the frontend as independent Deployments, plus optional Postgres/RabbitMQ/MinIO/Meilisearch/Keycloak dependencies.

**Homepage:** <https://github.com/dtrouillet/easywork>

Production deployment target for the easywork DMS — see the root `CLAUDE.md`
("Production deployment — Helm chart" section) for the overall design, and
`docs/adr/` for the architectural decisions behind it (in particular
[ADR 0005](../../../docs/adr/0005-helm-chart-third-party-dependency-charts.md)
for the third-party dependency choices and
[ADR 0006](../../../docs/adr/0006-optional-bundled-keycloak-identity-provider.md)
for the optional bundled Keycloak).

## Prerequisites

- Kubernetes >=1.26.0-0
- Helm 3.8+ (for `oci://` dependency support)
- An Ingress controller (nginx assumed by the defaults below) and
  [cert-manager](https://cert-manager.io/) with a `ClusterIssuer` — or set
  `ingress.tls.enabled: false` / bring your own certificates
- A real DNS hostname pointed at your ingress (two hostnames if you enable the
  bundled Keycloak — see below)

## Quick start — family tier (bundled Keycloak, zero external IdP)

`values-family.yaml` sets single-replica sizing and `keycloak.enabled: true`,
so this is enough for a fully working install with nothing external to stand
up first. You still need to provide a few install-specific values: real
hostnames, and the credentials this chart deliberately never defaults (see
"Secrets" below).

```bash
helm dependency update deploy/helm/easywork

cat > my-family-values.yaml <<'EOF'
ingress:
  host: easywork.example.com          # -> the app itself
  tls:
    clusterIssuer: letsencrypt-prod   # or tls.enabled: false if you're not using cert-manager
frontend:
  env:
    apiUrl: https://easywork.example.com
    nextAuthUrl: https://easywork.example.com
cors:
  allowedOrigins: https://easywork.example.com
keycloak:
  keycloak:
    hostname: https://auth.easywork.example.com   # MUST differ from ingress.host — see ADR 0006
  ingress:
    hosts:
      - host: auth.easywork.example.com
        paths:
          - path: /
            pathType: Prefix
    tls:
      - secretName: easywork-keycloak-tls
        hosts:
          - auth.easywork.example.com
EOF

helm install easywork deploy/helm/easywork \
  --namespace easywork --create-namespace \
  -f deploy/helm/easywork/values-family.yaml \
  -f my-family-values.yaml \
  --set-string postgresql.auth.password="$(openssl rand -base64 24)" \
  --set-string rabbitmq.auth.password="$(openssl rand -base64 24)" \
  --set-string minio.auth.rootPassword="$(openssl rand -base64 24)" \
  --set-string meilisearch.auth.masterKey="$(openssl rand -base64 24)" \
  --set-string app.nextAuthSecret="$(openssl rand -base64 32)"
```

`app.keycloakClientSecret`, `oauth2.jwtIssuerUri` and `app.keycloakIssuer` are
**not** set above — with `keycloak.enabled: true` they're auto-generated /
auto-derived (see `templates/secret.yaml` and `_helpers.tpl`'s
`easywork.oauthIssuerUri`/`oauthJwkSetUri`).

After install, follow the `NOTES.txt` output: it prints the Keycloak Admin
Console URL and the `kubectl` command to retrieve the auto-generated admin
password. No application user is seeded — create your first one via the
Admin Console before anyone can log in.

⚠️ The frontend pod calls Keycloak's *public* hostname directly for
server-side token refresh (`frontend/src/lib/auth.ts`), not just via browser
redirect — it must be reachable from **inside** the cluster, which needs
hairpin-NAT/LoadBalancer support or split-horizon DNS, not just a public DNS
record. See ADR 0006 for details if login redirects work but the callback
then fails.

## Quick start — SME tier (bring your own IdP)

`values-sme.yaml` adds HA sizing, HPA, and PodDisruptionBudgets, and leaves
`keycloak.enabled: false` — point it at your organization's existing IdP:

```bash
helm dependency update deploy/helm/easywork

helm install easywork deploy/helm/easywork \
  --namespace easywork --create-namespace \
  -f deploy/helm/easywork/values-sme.yaml \
  --set ingress.host=easywork.example.com \
  --set frontend.env.apiUrl=https://easywork.example.com \
  --set frontend.env.nextAuthUrl=https://easywork.example.com \
  --set cors.allowedOrigins=https://easywork.example.com \
  --set oauth2.jwtIssuerUri=https://your-idp.example.com/realms/easywork \
  --set app.keycloakIssuer=https://your-idp.example.com/realms/easywork \
  --set app.keycloakClientSecret="<your-oauth-client-secret>" \
  --set-string postgresql.auth.password="$(openssl rand -base64 24)" \
  --set-string rabbitmq.auth.password="$(openssl rand -base64 24)" \
  --set-string minio.auth.rootPassword="$(openssl rand -base64 24)" \
  --set-string meilisearch.auth.masterKey="$(openssl rand -base64 24)" \
  --set-string app.nextAuthSecret="$(openssl rand -base64 32)"
```

For a real production rollout, prefer `externalSecrets.enabled: true` (needs
the [external-secrets](https://external-secrets.io/) operator) or
`secrets.existingSecret` over passing raw passwords on the command line — see
"Secrets" below.

## Secrets

Every credential is either **required** (fails `helm install` loudly if
missing — never a silent default) or **auto-generated** and kept stable
across `helm upgrade`:

| Value | Family tier (`keycloak.enabled: true`) | SME tier (`keycloak.enabled: false`) |
|---|---|---|
| `postgresql.auth.password`, `rabbitmq.auth.password`, `minio.auth.rootPassword`, `meilisearch.auth.masterKey` | Required | Required |
| `app.nextAuthSecret` | Required | Required |
| `app.keycloakClientSecret` | Auto-generated | Required |
| `oauth2.jwtIssuerUri`, `app.keycloakIssuer` | Auto-derived from the bundled instance | Required |
| Keycloak admin password, embedded DB password | Auto-generated (only when `keycloak.enabled: true`) | N/A |

Three escape hatches, in order of how much they take over:
- `secrets.existingSecret` — bring your own single Secret with every key the
  chart would otherwise generate (see `templates/secret.yaml` for exact key
  names); disables `templates/secret.yaml` entirely.
- `externalSecrets.enabled` — source every credential from an
  [external-secrets.io](https://external-secrets.io/) `(Cluster)SecretStore`
  instead (see `templates/external-secret.yaml` and `externalSecrets.paths`).
- Per-dependency `external<Dep>` blocks (`externalDatabase`,
  `externalRabbitmq`, `externalMinio`, `externalMeilisearch`) — point a
  specific dependency at an already-running instance instead of the bundled
  sub-chart, independent of the two options above.

## Dependencies

| Repository | Name | Version |
|------------|------|---------|
| https://meilisearch.github.io/meilisearch-kubernetes | meilisearch | 0.36.0 |
| oci://registry-1.docker.io/cloudpirates | keycloak | 0.21.19 |
| oci://registry-1.docker.io/cloudpirates | minio | 0.13.1 |
| oci://registry-1.docker.io/cloudpirates | postgresql(postgres) | 0.19.12 |
| oci://registry-1.docker.io/cloudpirates | rabbitmq | 0.21.12 |

All four are condition-gated (`<dep>.enabled`) CloudPirates/official charts —
see [ADR 0005](../../../docs/adr/0005-helm-chart-third-party-dependency-charts.md)
for why CloudPirates over Bitnami, and
[ADR 0006](../../../docs/adr/0006-optional-bundled-keycloak-identity-provider.md)
for why Keycloak is bundled-but-disabled-by-default with its own dedicated
Ingress and embedded database.

## Values

Full reference — every field below is overridable; `values.yaml` is the
source of truth for defaults, and `values-family.yaml`/`values-sme.yaml` are
overlay files layered on top with `-f` as shown above.

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| app.keycloakClientId | string | `"easywork"` | OAuth client ID registered in your IdP (or auto-provisioned when keycloak.enabled: true). |
| app.keycloakClientSecret | string | `""` | OAuth client secret (required unless keycloak.enabled: true, which auto-generates it). |
| app.keycloakIssuer | string | `""` | OIDC issuer URI for NextAuth (required unless keycloak.enabled: true). |
| app.nextAuthSecret | string | `""` | NextAuth session-signing secret (required). |
| cors.allowedOrigins | string | `""` | Comma-separated list of origins allowed to call the backend. |
| docService.affinity | object | `{}` |  |
| docService.autoscaling.enabled | bool | `false` |  |
| docService.autoscaling.maxReplicas | int | `6` |  |
| docService.autoscaling.minReplicas | int | `2` |  |
| docService.autoscaling.targetCPUUtilizationPercentage | int | `75` |  |
| docService.extraEnv | list | `[]` |  |
| docService.image.digest | string | `""` |  |
| docService.image.pullPolicy | string | `"IfNotPresent"` |  |
| docService.image.registry | string | `"ghcr.io"` |  |
| docService.image.repository | string | `"dtrouillet/easywork-backend"` |  |
| docService.image.tag | string | `""` |  |
| docService.nodeSelector | object | `{}` |  |
| docService.podAnnotations | object | `{}` |  |
| docService.podDisruptionBudget.enabled | bool | `false` |  |
| docService.podDisruptionBudget.minAvailable | int | `1` |  |
| docService.podLabels | object | `{}` |  |
| docService.podSecurityContext.fsGroup | int | `1000` |  |
| docService.podSecurityContext.runAsGroup | int | `1000` |  |
| docService.podSecurityContext.runAsNonRoot | bool | `true` |  |
| docService.podSecurityContext.runAsUser | int | `1000` |  |
| docService.probes.liveness.failureThreshold | int | `3` |  |
| docService.probes.liveness.initialDelaySeconds | int | `45` |  |
| docService.probes.liveness.path | string | `"/actuator/health/liveness"` |  |
| docService.probes.liveness.periodSeconds | int | `15` |  |
| docService.probes.liveness.timeoutSeconds | int | `5` |  |
| docService.probes.readiness.failureThreshold | int | `3` |  |
| docService.probes.readiness.initialDelaySeconds | int | `20` |  |
| docService.probes.readiness.path | string | `"/actuator/health/readiness"` |  |
| docService.probes.readiness.periodSeconds | int | `10` |  |
| docService.probes.readiness.timeoutSeconds | int | `5` |  |
| docService.replicaCount | int | `2` |  |
| docService.resources.limits.cpu | string | `"2"` |  |
| docService.resources.limits.memory | string | `"1536Mi"` |  |
| docService.resources.requests.cpu | string | `"500m"` |  |
| docService.resources.requests.memory | string | `"768Mi"` |  |
| docService.securityContext.allowPrivilegeEscalation | bool | `false` |  |
| docService.securityContext.capabilities.drop[0] | string | `"ALL"` |  |
| docService.securityContext.readOnlyRootFilesystem | bool | `true` |  |
| docService.service.enabled | bool | `true` |  |
| docService.service.port | int | `8080` |  |
| docService.service.type | string | `"ClusterIP"` |  |
| docService.springProfilesActive | string | `""` |  |
| docService.tolerations | list | `[]` |  |
| externalDatabase.database | string | `"easywork"` |  |
| externalDatabase.existingSecret | string | `""` |  |
| externalDatabase.host | string | `""` |  |
| externalDatabase.passwordKey | string | `"password"` |  |
| externalDatabase.port | int | `5432` |  |
| externalDatabase.usernameKey | string | `"username"` |  |
| externalMeilisearch.apiKeyKey | string | `"MEILI_MASTER_KEY"` |  |
| externalMeilisearch.existingSecret | string | `""` |  |
| externalMeilisearch.host | string | `""` |  |
| externalMinio.accessKeyKey | string | `"user"` |  |
| externalMinio.bucket | string | `"easywork-documents"` |  |
| externalMinio.endpoint | string | `""` |  |
| externalMinio.existingSecret | string | `""` |  |
| externalMinio.secretKeyKey | string | `"password"` |  |
| externalRabbitmq.existingSecret | string | `""` |  |
| externalRabbitmq.host | string | `""` |  |
| externalRabbitmq.passwordKey | string | `"password"` |  |
| externalRabbitmq.port | int | `5672` |  |
| externalRabbitmq.usernameKey | string | `"username"` |  |
| externalSecrets.backend.kind | string | `"ClusterSecretStore"` |  |
| externalSecrets.backend.name | string | `"vault-backend"` |  |
| externalSecrets.enabled | bool | `false` | Source every credential from an external-secrets.io store instead of generating plain Kubernetes Secrets. |
| externalSecrets.paths.app | string | `"easywork/app"` |  |
| externalSecrets.paths.keycloak | string | `"easywork/keycloak"` |  |
| externalSecrets.paths.meilisearch | string | `"easywork/meilisearch"` |  |
| externalSecrets.paths.minio | string | `"easywork/minio"` |  |
| externalSecrets.paths.postgres | string | `"easywork/postgres"` |  |
| externalSecrets.paths.rabbitmq | string | `"easywork/rabbitmq"` |  |
| externalSecrets.refreshInterval | string | `"1h"` |  |
| frontend.affinity | object | `{}` |  |
| frontend.env.apiUrl | string | `""` |  |
| frontend.env.nextAuthUrl | string | `""` |  |
| frontend.extraEnv | list | `[]` |  |
| frontend.image.digest | string | `""` |  |
| frontend.image.pullPolicy | string | `"IfNotPresent"` |  |
| frontend.image.registry | string | `"ghcr.io"` |  |
| frontend.image.repository | string | `"dtrouillet/easywork-frontend"` |  |
| frontend.image.tag | string | `""` |  |
| frontend.nodeSelector | object | `{}` |  |
| frontend.podAnnotations | object | `{}` |  |
| frontend.podDisruptionBudget.enabled | bool | `false` |  |
| frontend.podDisruptionBudget.minAvailable | int | `1` |  |
| frontend.podLabels | object | `{}` |  |
| frontend.podSecurityContext.fsGroup | int | `1000` |  |
| frontend.podSecurityContext.runAsGroup | int | `1000` |  |
| frontend.podSecurityContext.runAsNonRoot | bool | `true` |  |
| frontend.podSecurityContext.runAsUser | int | `1000` |  |
| frontend.probes.liveness.failureThreshold | int | `3` |  |
| frontend.probes.liveness.initialDelaySeconds | int | `15` |  |
| frontend.probes.liveness.path | string | `"/api/health"` |  |
| frontend.probes.liveness.periodSeconds | int | `15` |  |
| frontend.probes.liveness.timeoutSeconds | int | `3` |  |
| frontend.probes.readiness.failureThreshold | int | `3` |  |
| frontend.probes.readiness.initialDelaySeconds | int | `10` |  |
| frontend.probes.readiness.path | string | `"/api/health"` |  |
| frontend.probes.readiness.periodSeconds | int | `10` |  |
| frontend.probes.readiness.timeoutSeconds | int | `3` |  |
| frontend.replicaCount | int | `2` |  |
| frontend.resources.limits.cpu | string | `"500m"` |  |
| frontend.resources.limits.memory | string | `"512Mi"` |  |
| frontend.resources.requests.cpu | string | `"100m"` |  |
| frontend.resources.requests.memory | string | `"128Mi"` |  |
| frontend.securityContext.allowPrivilegeEscalation | bool | `false` |  |
| frontend.securityContext.capabilities.drop[0] | string | `"ALL"` |  |
| frontend.securityContext.readOnlyRootFilesystem | bool | `false` |  |
| frontend.service.port | int | `3000` |  |
| frontend.service.type | string | `"ClusterIP"` |  |
| frontend.tolerations | list | `[]` |  |
| fullnameOverride | string | `""` |  |
| global.commonAnnotations | object | `{}` |  |
| global.commonLabels | object | `{}` |  |
| global.imagePullSecrets | list | `[]` |  |
| global.imageRegistry | string | `""` | Overrides every component's own image registry (self and dependencies) in one place — point this at a private mirror/pull-through cache. |
| ingestWorker.affinity | object | `{}` |  |
| ingestWorker.autoscaling.enabled | bool | `true` |  |
| ingestWorker.autoscaling.maxReplicas | int | `10` |  |
| ingestWorker.autoscaling.minReplicas | int | `2` |  |
| ingestWorker.autoscaling.targetCPUUtilizationPercentage | int | `70` |  |
| ingestWorker.extraEnv | list | `[]` |  |
| ingestWorker.image.digest | string | `""` |  |
| ingestWorker.image.pullPolicy | string | `"IfNotPresent"` |  |
| ingestWorker.image.registry | string | `"ghcr.io"` |  |
| ingestWorker.image.repository | string | `"dtrouillet/easywork-backend"` |  |
| ingestWorker.image.tag | string | `""` |  |
| ingestWorker.image.tagSuffix | string | `"-ocr"` |  |
| ingestWorker.nodeSelector | object | `{}` |  |
| ingestWorker.ocr.ocrLanguages | string | `""` |  |
| ingestWorker.ocr.tessdataPath | string | `""` |  |
| ingestWorker.podAnnotations | object | `{}` |  |
| ingestWorker.podDisruptionBudget.enabled | bool | `false` |  |
| ingestWorker.podDisruptionBudget.minAvailable | int | `1` |  |
| ingestWorker.podLabels | object | `{}` |  |
| ingestWorker.podSecurityContext.fsGroup | int | `1000` |  |
| ingestWorker.podSecurityContext.runAsGroup | int | `1000` |  |
| ingestWorker.podSecurityContext.runAsNonRoot | bool | `true` |  |
| ingestWorker.podSecurityContext.runAsUser | int | `1000` |  |
| ingestWorker.probes.liveness.failureThreshold | int | `3` |  |
| ingestWorker.probes.liveness.initialDelaySeconds | int | `45` |  |
| ingestWorker.probes.liveness.path | string | `"/actuator/health/liveness"` |  |
| ingestWorker.probes.liveness.periodSeconds | int | `15` |  |
| ingestWorker.probes.liveness.timeoutSeconds | int | `5` |  |
| ingestWorker.probes.readiness.failureThreshold | int | `3` |  |
| ingestWorker.probes.readiness.initialDelaySeconds | int | `20` |  |
| ingestWorker.probes.readiness.path | string | `"/actuator/health/readiness"` |  |
| ingestWorker.probes.readiness.periodSeconds | int | `10` |  |
| ingestWorker.probes.readiness.timeoutSeconds | int | `5` |  |
| ingestWorker.replicaCount | int | `2` |  |
| ingestWorker.resources.limits.cpu | string | `"4"` |  |
| ingestWorker.resources.limits.memory | string | `"3Gi"` |  |
| ingestWorker.resources.requests.cpu | string | `"1"` |  |
| ingestWorker.resources.requests.memory | string | `"1Gi"` |  |
| ingestWorker.securityContext.allowPrivilegeEscalation | bool | `false` |  |
| ingestWorker.securityContext.capabilities.drop[0] | string | `"ALL"` |  |
| ingestWorker.securityContext.readOnlyRootFilesystem | bool | `true` |  |
| ingestWorker.service.enabled | bool | `true` |  |
| ingestWorker.service.port | int | `8080` |  |
| ingestWorker.service.type | string | `"ClusterIP"` |  |
| ingestWorker.springProfilesActive | string | `"ingest"` |  |
| ingestWorker.tolerations | list | `[]` |  |
| ingress.annotations | object | `{}` |  |
| ingress.className | string | `"nginx"` |  |
| ingress.enabled | bool | `true` | Create the Ingress routing `/` to frontend and `/api` to doc-service. |
| ingress.host | string | `"easywork.example.com"` | Public hostname for the whole app (frontend + API). Change this. |
| ingress.tls.clusterIssuer | string | `"letsencrypt-prod"` |  |
| ingress.tls.enabled | bool | `true` | Provision TLS via the cert-manager ClusterIssuer below. |
| ingress.tls.secretName | string | `"easywork-tls"` |  |
| keycloak.database.type | string | `"postgres"` |  |
| keycloak.enabled | bool | `false` | Bundle a Keycloak instance instead of requiring an external IdP. See docs/adr/0006-optional-bundled-keycloak-identity-provider.md. |
| keycloak.fullnameOverride | string | `"easywork-keycloak"` |  |
| keycloak.image.registry | string | `"docker.io"` |  |
| keycloak.image.repository | string | `"keycloak/keycloak"` |  |
| keycloak.image.tag | string | `"26.7.0@sha256:0f198be292568439d700cdbfb893e69a6009bb43a94a06a945b1d3d506c76b13"` |  |
| keycloak.ingress.className | string | `"nginx"` |  |
| keycloak.ingress.enabled | bool | `true` | Create a dedicated Ingress for Keycloak (separate hostname from the main `ingress.host` — see docs/adr/0006). |
| keycloak.ingress.hosts[0] | object | `{"host":"","paths":[{"path":"/","pathType":"Prefix"}]}` | Same hostname as keycloak.keycloak.hostname, without the scheme. |
| keycloak.ingress.tls[0].hosts | list | `[]` |  |
| keycloak.ingress.tls[0].secretName | string | `"easywork-keycloak-tls"` |  |
| keycloak.keycloak.adminUser | string | `"admin"` |  |
| keycloak.keycloak.existingSecret | string | `"easywork-keycloak-admin-auth"` |  |
| keycloak.keycloak.hostname | string | `""` | Public URL of the bundled Keycloak, incl. scheme (required when keycloak.enabled: true — see keycloak.ingress.hosts below). |
| keycloak.keycloak.hostnameStrict | bool | `true` |  |
| keycloak.keycloak.httpEnabled | bool | `true` |  |
| keycloak.keycloak.production | bool | `true` |  |
| keycloak.keycloak.proxyHeaders | string | `"xforwarded"` |  |
| keycloak.postgres.auth.database | string | `"keycloak"` |  |
| keycloak.postgres.auth.existingSecret | string | `"easywork-keycloak-db-auth"` |  |
| keycloak.postgres.auth.username | string | `"keycloak"` |  |
| keycloak.postgres.enabled | bool | `true` | Bundle a dedicated Postgres instance for Keycloak. |
| keycloak.postgres.persistence.size | string | `"5Gi"` |  |
| keycloak.postgres.resources.limits.cpu | string | `"1"` |  |
| keycloak.postgres.resources.limits.memory | string | `"512Mi"` |  |
| keycloak.postgres.resources.requests.cpu | string | `"250m"` |  |
| keycloak.postgres.resources.requests.memory | string | `"256Mi"` |  |
| keycloak.realm.existingSecret | string | `"easywork-keycloak-realm-auth"` |  |
| keycloak.realm.import | bool | `true` | Auto-import the generated realm/client on startup. |
| keycloak.resources.limits.cpu | string | `"1500m"` |  |
| keycloak.resources.limits.memory | string | `"1536Mi"` |  |
| keycloak.resources.requests.cpu | string | `"500m"` |  |
| keycloak.resources.requests.memory | string | `"768Mi"` |  |
| keycloakRealm.displayName | string | `"easywork"` |  |
| keycloakRealm.name | string | `"easywork"` |  |
| meilisearch.auth.existingMasterKeySecret | string | `"easywork-meilisearch-auth"` |  |
| meilisearch.auth.masterKey | string | `""` |  |
| meilisearch.enabled | bool | `true` | Bundle a Meilisearch instance. |
| meilisearch.environment.MEILI_ENV | string | `"production"` |  |
| meilisearch.fullnameOverride | string | `"easywork-meilisearch"` |  |
| meilisearch.persistence.enabled | bool | `true` |  |
| meilisearch.persistence.size | string | `"10Gi"` |  |
| meilisearch.resources.limits.cpu | string | `"1"` |  |
| meilisearch.resources.limits.memory | string | `"1Gi"` |  |
| meilisearch.resources.requests.cpu | string | `"250m"` |  |
| meilisearch.resources.requests.memory | string | `"512Mi"` |  |
| minio.auth.existingSecret | string | `"easywork-minio-auth"` |  |
| minio.auth.rootPassword | string | `""` |  |
| minio.auth.rootUser | string | `"easywork"` |  |
| minio.defaultBuckets | string | `"easywork-documents"` |  |
| minio.enabled | bool | `true` | Bundle a MinIO instance for document storage. |
| minio.fullnameOverride | string | `"easywork-minio"` |  |
| minio.image.registry | string | `"docker.io"` |  |
| minio.image.repository | string | `"cloudpirates/image-minio"` |  |
| minio.image.tag | string | `"RELEASE.2025-10-15T17-29-55Z-hardened@sha256:8dc02a7e509336c8bbb67962086d691bdcde20a2c9327ed68e5081a681f6dbfc"` |  |
| minio.persistence.size | string | `"50Gi"` |  |
| minio.persistence.storageClass | string | `""` |  |
| nameOverride | string | `""` |  |
| networkPolicy.enabled | bool | `true` | Enable default-deny NetworkPolicies with explicit allow rules. |
| networkPolicy.ingressNamespaceSelector | object | `{}` |  |
| networkPolicy.ingressPodSelector | object | `{}` |  |
| oauth2.jwtIssuerUri | string | `""` | OIDC issuer URI (required unless keycloak.enabled: true). |
| oauth2.jwtJwkSetUri | string | `""` | In-cluster JWK-set URL override (optional, auto-derived when keycloak.enabled: true). |
| postgresql.auth.database | string | `"easywork"` |  |
| postgresql.auth.existingSecret | string | `"easywork-postgres-auth"` |  |
| postgresql.auth.password | string | `""` |  |
| postgresql.auth.username | string | `"easywork"` |  |
| postgresql.enabled | bool | `true` | Bundle a Postgres instance for the app's own database. |
| postgresql.fullnameOverride | string | `"easywork-postgresql"` |  |
| postgresql.image.registry | string | `"docker.io"` |  |
| postgresql.image.repository | string | `"postgres"` |  |
| postgresql.image.tag | string | `"18.4@sha256:3a82e1f56c8f0f5616a11103ac3d47e632c3938698946a7ad26da0df1334744a"` |  |
| postgresql.persistence.size | string | `"20Gi"` |  |
| postgresql.replication.enabled | bool | `false` |  |
| rabbitmq.auth.existingSecret | string | `"easywork-rabbitmq-auth"` |  |
| rabbitmq.auth.password | string | `""` |  |
| rabbitmq.auth.username | string | `"easywork"` |  |
| rabbitmq.enabled | bool | `true` | Bundle a RabbitMQ instance. |
| rabbitmq.fullnameOverride | string | `"easywork-rabbitmq"` |  |
| rabbitmq.image.registry | string | `"docker.io"` |  |
| rabbitmq.image.repository | string | `"rabbitmq"` |  |
| rabbitmq.image.tag | string | `"4.3.4-management@sha256:4e628d3cbc61ef45c5918e19bb9844874410d96d4ced897ced7d072d63ad555c"` |  |
| rabbitmq.pdb.create | bool | `false` |  |
| rabbitmq.peerDiscoveryK8sPlugin.enabled | bool | `false` |  |
| rabbitmq.persistence.size | string | `"10Gi"` |  |
| rabbitmq.replicaCount | int | `1` |  |
| rbac.create | bool | `false` |  |
| rbac.rules | list | `[]` |  |
| searchService.affinity | object | `{}` |  |
| searchService.extraEnv | list | `[]` |  |
| searchService.image.digest | string | `""` |  |
| searchService.image.pullPolicy | string | `"IfNotPresent"` |  |
| searchService.image.registry | string | `"ghcr.io"` |  |
| searchService.image.repository | string | `"dtrouillet/easywork-backend"` |  |
| searchService.image.tag | string | `""` |  |
| searchService.nodeSelector | object | `{}` |  |
| searchService.podAnnotations | object | `{}` |  |
| searchService.podDisruptionBudget.enabled | bool | `false` |  |
| searchService.podDisruptionBudget.minAvailable | int | `1` |  |
| searchService.podLabels | object | `{}` |  |
| searchService.podSecurityContext.fsGroup | int | `1000` |  |
| searchService.podSecurityContext.runAsGroup | int | `1000` |  |
| searchService.podSecurityContext.runAsNonRoot | bool | `true` |  |
| searchService.podSecurityContext.runAsUser | int | `1000` |  |
| searchService.probes.liveness.failureThreshold | int | `3` |  |
| searchService.probes.liveness.initialDelaySeconds | int | `45` |  |
| searchService.probes.liveness.path | string | `"/actuator/health/liveness"` |  |
| searchService.probes.liveness.periodSeconds | int | `15` |  |
| searchService.probes.liveness.timeoutSeconds | int | `5` |  |
| searchService.probes.readiness.failureThreshold | int | `3` |  |
| searchService.probes.readiness.initialDelaySeconds | int | `20` |  |
| searchService.probes.readiness.path | string | `"/actuator/health/readiness"` |  |
| searchService.probes.readiness.periodSeconds | int | `10` |  |
| searchService.probes.readiness.timeoutSeconds | int | `5` |  |
| searchService.replicaCount | int | `1` |  |
| searchService.resources.limits.cpu | string | `"1"` |  |
| searchService.resources.limits.memory | string | `"1Gi"` |  |
| searchService.resources.requests.cpu | string | `"250m"` |  |
| searchService.resources.requests.memory | string | `"512Mi"` |  |
| searchService.securityContext.allowPrivilegeEscalation | bool | `false` |  |
| searchService.securityContext.capabilities.drop[0] | string | `"ALL"` |  |
| searchService.securityContext.readOnlyRootFilesystem | bool | `true` |  |
| searchService.service.enabled | bool | `true` |  |
| searchService.service.port | int | `8080` |  |
| searchService.service.type | string | `"ClusterIP"` |  |
| searchService.springProfilesActive | string | `"search"` |  |
| searchService.tolerations | list | `[]` |  |
| secrets.existingSecret | string | `""` | Name of a Secret you manage yourself instead of letting the chart generate one. Skips templates/secret.yaml entirely when set. |
| serviceAccount.annotations | object | `{}` |  |
| serviceAccount.automountServiceAccountToken | bool | `true` |  |
| serviceAccount.create | bool | `true` |  |
| serviceAccount.name | string | `""` |  |

## Regenerating this file

This README (except the "Quick start"/"Secrets" prose above, which live in
`README.md.gotmpl`) is generated from `values.yaml`'s `# --` comments via
[helm-docs](https://github.com/norwoodj/helm-docs) — re-run it after changing
`values.yaml` or `Chart.yaml`:

```bash
helm-docs --chart-search-root deploy/helm
```

## Maintainers

| Name | Email | Url |
| ---- | ------ | --- |
| easywork maintainers |  |  |
