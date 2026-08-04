{{/*
Base name for the chart.
*/}}
{{- define "easywork.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Release-scoped fullname, e.g. "myrelease-easywork". If the release name already
contains the chart name, avoid the double repetition.
*/}}
{{- define "easywork.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := default .Chart.Name .Values.nameOverride -}}
{{- if contains $name .Release.Name -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{- define "easywork.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Common labels, applied to every resource this chart renders directly (not the
subcharts, which label themselves).
*/}}
{{- define "easywork.labels" -}}
helm.sh/chart: {{ include "easywork.chart" . }}
{{ include "easywork.selectorLabels" (dict "root" . "component" "") }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- with .Values.global.commonLabels }}
{{ toYaml . }}
{{- end }}
{{- end -}}

{{/*
Selector labels, parametrized by component (doc-service/ingest-worker/search-service/frontend).
Call as: include "easywork.selectorLabels" (dict "root" $ "component" "doc-service")
*/}}
{{- define "easywork.selectorLabels" -}}
app.kubernetes.io/name: {{ include "easywork.name" .root }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
{{- if .component }}
app.kubernetes.io/component: {{ .component }}
{{- end }}
{{- end -}}

{{/*
Full labels for a specific component (common labels + component selector label).
Call as: include "easywork.componentLabels" (dict "root" $ "component" "doc-service")
*/}}
{{- define "easywork.componentLabels" -}}
{{ include "easywork.labels" .root }}
app.kubernetes.io/component: {{ .component }}
{{- end -}}

{{- define "easywork.serviceAccountName" -}}
{{- if .Values.serviceAccount.create -}}
{{- default (include "easywork.fullname" .) .Values.serviceAccount.name -}}
{{- else -}}
{{- default "default" .Values.serviceAccount.name -}}
{{- end -}}
{{- end -}}

{{/*
Name of the Secret holding a given credential domain. Resolution order:
1. .Values.secrets.existingSecret (global escape hatch — user pre-created everything)
2. the fixed per-domain name this chart renders itself (plain Secret or ExternalSecret)
Call as: include "easywork.secretName" (dict "root" $ "domain" "postgres")
domain is one of: postgres | rabbitmq | minio | meilisearch | app
*/}}
{{- define "easywork.secretName" -}}
{{- if .root.Values.secrets.existingSecret -}}
{{- .root.Values.secrets.existingSecret -}}
{{- else -}}
{{- printf "%s-%s-auth" (include "easywork.fullname" .root) .domain -}}
{{- end -}}
{{- end -}}

{{/*
JDBC datasource URL — bundled postgresql subchart or externalDatabase.
*/}}
{{- define "easywork.datasourceUrl" -}}
{{- if .Values.postgresql.enabled -}}
{{- printf "jdbc:postgresql://%s:5432/%s" .Values.postgresql.fullnameOverride .Values.postgresql.auth.database -}}
{{- else -}}
{{- printf "jdbc:postgresql://%s:%d/%s" .Values.externalDatabase.host (.Values.externalDatabase.port | int) .Values.externalDatabase.database -}}
{{- end -}}
{{- end -}}

{{/*
RabbitMQ host (bare hostname, not a URL — SPRING_RABBITMQ_HOST/PORT are separate).
*/}}
{{- define "easywork.rabbitmqHost" -}}
{{- if .Values.rabbitmq.enabled -}}
{{- .Values.rabbitmq.fullnameOverride -}}
{{- else -}}
{{- .Values.externalRabbitmq.host -}}
{{- end -}}
{{- end -}}

{{- define "easywork.rabbitmqPort" -}}
{{- if .Values.rabbitmq.enabled -}}
5672
{{- else -}}
{{- .Values.externalRabbitmq.port -}}
{{- end -}}
{{- end -}}

{{/*
MinIO endpoint — full URL, matching what StorageProperties.endpoint expects.
*/}}
{{- define "easywork.minioEndpoint" -}}
{{- if .Values.minio.enabled -}}
{{- printf "http://%s:9000" .Values.minio.fullnameOverride -}}
{{- else -}}
{{- .Values.externalMinio.endpoint -}}
{{- end -}}
{{- end -}}

{{- define "easywork.minioBucket" -}}
{{- if .Values.minio.enabled -}}
{{- .Values.minio.defaultBuckets -}}
{{- else -}}
{{- .Values.externalMinio.bucket -}}
{{- end -}}
{{- end -}}

{{/*
Meilisearch host — full URL, matching what SearchProperties.host expects.
*/}}
{{- define "easywork.meilisearchHost" -}}
{{- if .Values.meilisearch.enabled -}}
{{- printf "http://%s:7700" .Values.meilisearch.fullnameOverride -}}
{{- else -}}
{{- .Values.externalMeilisearch.host -}}
{{- end -}}
{{- end -}}

{{/*
Public-facing OIDC issuer URL, baked into JWT `iss` claims. Must be reachable
by both the browser (NextAuth's redirect flow) and, per
frontend/src/lib/auth.ts's single-issuer design, the frontend pod itself for
server-side token exchange/refresh.
*/}}
{{- define "easywork.oauthIssuerUri" -}}
{{- if .Values.keycloak.enabled -}}
{{- printf "%s/realms/%s" .Values.keycloak.keycloak.hostname .Values.keycloakRealm.name -}}
{{- else -}}
{{- required "oauth2.jwtIssuerUri is required (or set keycloak.enabled=true)" .Values.oauth2.jwtIssuerUri -}}
{{- end -}}
{{- end -}}

{{/*
In-cluster JWK-set URL, so backend pods fetch signing keys without round-
tripping through the public ingress. Only the backend resource-server config
gets this optimization — the frontend has no equivalent split (see auth.ts).
*/}}
{{- define "easywork.oauthJwkSetUri" -}}
{{- if .Values.keycloak.enabled -}}
{{- printf "http://%s:%d/realms/%s/protocol/openid-connect/certs" .Values.keycloak.fullnameOverride (.Values.keycloak.service.httpPort | int) .Values.keycloakRealm.name -}}
{{- else -}}
{{- .Values.oauth2.jwtJwkSetUri -}}
{{- end -}}
{{- end -}}

{{/*
Full image reference for one of our own components. Prefers `.digest` (pin by
digest, per CLAUDE.md's "never latest" rule) over `.tag`, falling back to the
chart's own appVersion (plus `.image.tagSuffix` if set — e.g. ingest-worker's
"-ocr", since backend/Dockerfile's runtime vs runtime-ocr targets are
published under the same repository with different tag suffixes by
.github/workflows/release.yml, not different repositories) if neither `.tag`
nor `.digest` is set. `global.imageRegistry` overrides the per-component
registry when set (single place to redirect every image through a private
mirror/pull-through cache).
Call as: include "easywork.imageRef" (dict "root" $ "image" .Values.docService.image)
*/}}
{{- define "easywork.imageRef" -}}
{{- $registry := .root.Values.global.imageRegistry | default .image.registry -}}
{{- if .image.digest -}}
{{- printf "%s/%s@%s" $registry .image.repository .image.digest -}}
{{- else -}}
{{- printf "%s/%s:%s" $registry .image.repository (.image.tag | default (printf "%s%s" .root.Chart.AppVersion (.image.tagSuffix | default ""))) -}}
{{- end -}}
{{- end -}}

{{/*
Env vars every backend component (doc-service/ingest-worker/search-service)
needs regardless of role — bound by the always-active `document` module and
un-profiled SecurityConfig, so every pod needs a working value even if it
never exercises the capability. Call as: include "easywork.backendCommonEnv" $
*/}}
{{- define "easywork.backendCommonEnv" -}}
- name: SPRING_DATASOURCE_URL
  value: {{ include "easywork.datasourceUrl" . | quote }}
- name: SPRING_DATASOURCE_USERNAME
  value: {{ .Values.postgresql.auth.username | quote }}
- name: SPRING_DATASOURCE_PASSWORD
  valueFrom:
    secretKeyRef:
      name: {{ include "easywork.secretName" (dict "root" . "domain" "postgres") }}
      key: postgres-password
- name: SPRING_RABBITMQ_HOST
  value: {{ include "easywork.rabbitmqHost" . | quote }}
- name: SPRING_RABBITMQ_PORT
  value: {{ include "easywork.rabbitmqPort" . | quote }}
- name: SPRING_RABBITMQ_USERNAME
  value: {{ .Values.rabbitmq.auth.username | quote }}
- name: SPRING_RABBITMQ_PASSWORD
  valueFrom:
    secretKeyRef:
      name: {{ include "easywork.secretName" (dict "root" . "domain" "rabbitmq") }}
      key: password
- name: MINIO_ENDPOINT
  value: {{ include "easywork.minioEndpoint" . | quote }}
- name: MINIO_ACCESS_KEY
  valueFrom:
    secretKeyRef:
      name: {{ include "easywork.secretName" (dict "root" . "domain" "minio") }}
      key: user
- name: MINIO_SECRET_KEY
  valueFrom:
    secretKeyRef:
      name: {{ include "easywork.secretName" (dict "root" . "domain" "minio") }}
      key: password
- name: MINIO_BUCKET
  value: {{ include "easywork.minioBucket" . | quote }}
- name: CORS_ALLOWED_ORIGINS
  value: {{ required "cors.allowedOrigins is required" .Values.cors.allowedOrigins | quote }}
- name: SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI
  value: {{ include "easywork.oauthIssuerUri" . | quote }}
{{- $jwkSetUri := include "easywork.oauthJwkSetUri" . }}
{{- if $jwkSetUri }}
- name: SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI
  value: {{ $jwkSetUri | quote }}
{{- end }}
{{- end -}}
