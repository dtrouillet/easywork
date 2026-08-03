# 0004 — Deliver DocumentUploadedEvent and IngestCompletedEvent via AMQP only

**Status:** Accepted

**Date:** 2026-08-03

## Context

`DocumentUploadedEvent` and `IngestCompletedEvent`
(`backend/src/main/java/fr/easywork/document/event/`) are both annotated
`@Externalized("<exchange>::#{#this.documentId}")` (Spring Modulith's
`spring-modulith-events-amqp`), which publishes them to a named RabbitMQ
exchange on every `ApplicationEventPublisher.publishEvent(...)` call. Nothing
in the codebase declared those exchanges, so every publish failed with
`NOT_FOUND - no exchange '...' in vhost '/'`, closing the AMQP channel —
visible as constant `CachingConnectionFactory` error-log spam.

This was more than log noise. Per CLAUDE.md, production deploys `doc-service`,
`ingest-worker` (`@Profile("ingest")`), and `search-service`
(`@Profile("search")`) as **separate pods** from the same image. Before this
change, the consumers —
`IngestEventConsumer.onDocumentUploaded`/`DocumentService.onIngestCompleted`
— were `@ApplicationModuleListener` (ADR 0002), which only fires in-process,
same JVM as the publisher. Local dev runs the whole app as one process
(`SPRING_PROFILES_ACTIVE: ingest,search` in `compose.yml`), so processing
happened correctly via the in-process path and the AMQP failure was invisible
except as log noise. In a real multi-pod deployment, a doc-service pod
publishing `DocumentUploadedEvent` would never reach an ingest-worker pod at
all — ingest would silently never run. There is no Helm chart yet
(`deploy/` doesn't exist), so this was never reachable in a live deployment;
this is a pre-emptive fix, not an incident response.

## Decision drivers

- Cross-pod delivery must actually work once a Helm chart deploys
  doc-service/ingest-worker/search-service as separate pods
- Local dev must not silently mask the production topology — the same code
  path should run in both
- No double-processing: local dev runs `ingest` in the same process as the
  publisher (`document`, always active), so any fix that adds a second
  delivery path alongside the existing in-process one would process every
  event twice there
- Keep `onIngestCompleted`'s existing `@Transactional(propagation =
  REQUIRES_NEW)` semantics intact
- Minimal new infrastructure — no DLQ/retry topology beyond what's already
  configured (`spring.rabbitmq.listener.simple.acknowledge-mode: manual` was
  already present in `application.yml`, unused until now — evidence this was
  the intended design, just never finished)

## Considered options

- **Option A (chosen)** — Route both events through AMQP exclusively, in dev
  and prod alike: keep `@Externalized` on the publisher side (Modulith already
  handles serialization + its event publication registry), remove
  `@ApplicationModuleListener` from both consumers, and add a `@RabbitListener`
  per event that calls the now-plain consumer method with manual ack/nack.
- **Option B (rejected)** — Keep `@ApplicationModuleListener` for in-process
  delivery and additionally add a `@RabbitListener` that republishes the
  message as an `ApplicationEvent` (a common "bridge AMQP back into
  in-process" pattern). Verified via `docker compose` that local dev runs
  `ingest` in the same process as the publisher — this would process every
  document twice locally (once in-process immediately, once again when the
  AMQP round trip completes).
- **Option C (rejected)** — Rely on a built-in Spring Modulith inbound bridge
  instead of hand-writing consumers. Verified against the actual Spring
  Modulith 2.1.0 source (`RabbitEventExternalizerConfiguration`) that
  `spring-modulith-events-amqp` is strictly outbound — there is no built-in
  mechanism to convert an inbound AMQP message back into a local
  `ApplicationEvent`, so a hand-rolled consumer is unavoidable either way.

## Decision outcome

**Chosen option:** A.

- `RabbitTopologyConfig`/`RabbitTopologyProperties`
  (`document/config`, always active — every pod, including publish-only
  doc-service pods, must declare the same topology at startup) declare a
  durable `FanoutExchange` + durable `Queue` + `Binding` for each event.
  Fanout (not topic+`#`) because the routing key is only a per-message
  document UUID needed to satisfy `@Externalized`'s `target::key` syntax, and
  there's exactly one queue per exchange.
- A `MessageConverter` bean (`JacksonJsonMessageConverter`, built from Boot's
  autoconfigured `JsonMapper`) is registered explicitly. Modulith's
  `RabbitJacksonConfiguration` only customizes the autoconfigured
  `RabbitTemplate` instance directly for the *outbound* (externalization)
  side — it does not register a standalone `MessageConverter` bean. Without
  one, the `@RabbitListener` container factory falls back to
  `SimpleMessageConverter` and fails to deserialize Modulith's JSON payloads.
  Confirmed via decompiling the actual `spring-modulith-events-amqp` 2.1.0 and
  `spring-boot-autoconfigure` 4.1.0 classes, not just the reference docs.
- `IngestEventConsumer.onDocumentUploaded` and
  `DocumentService.onIngestCompleted` lost `@ApplicationModuleListener` and
  are now plain methods, unchanged otherwise (bodies, signatures,
  `onIngestCompleted`'s `@Transactional(propagation = REQUIRES_NEW)` — all
  untouched).
- Two new `@RabbitListener` beans — `DocumentUploadedRabbitListener`
  (`ingest/consumer`, `@Profile("ingest")`) and
  `IngestCompletedRabbitListener` (`document/consumer`, no profile, matching
  `DocumentService`'s existing always-active status) — call the plain
  consumer methods and manually ack on success / nack (no requeue, no DLQ) on
  exception. Each lives in its **own bean** rather than being folded into
  `IngestEventConsumer`/`DocumentService` directly: `onIngestCompleted`
  carries `REQUIRES_NEW`, and a same-class self-invocation from an
  `@RabbitListener` method on that same bean would silently bypass the
  transactional proxy. A cross-bean call keeps `REQUIRES_NEW` working.
- New integration test
  (`DocumentUploadedAmqpRoundTripIntegrationTest`, `@ActiveProfiles("ingest")`
  merged onto `AbstractIntegrationTest`'s `"test"`) publishes a
  `DocumentUploadedEvent` inside a real committed transaction (via
  `TransactionTemplate` — Modulith's externalization listener only fires
  `AFTER_COMMIT`, and Spring's test-managed transactions roll back rather than
  commit, so `@Transactional` on the test itself would skip externalization
  the same way the bug did) and asserts, against the real Testcontainers
  RabbitMQ broker, that the mocked `IngestPipeline` was eventually invoked —
  proving the message actually crossed the broker rather than relying on
  same-JVM delivery like every other ingest test does.

### Positive consequences

- Ingest now actually works when doc-service and ingest-worker run as
  separate pods, not just in the single-process local dev topology
- Local dev now exercises the exact same delivery path as production —
  the previous split (in-process locally, broken in prod) can't silently
  regress again
- The `NOT_FOUND` error-log spam is gone; the two queues are now visible and
  inspectable in the RabbitMQ management UI
- No new dependencies; reuses the already-present
  `spring.rabbitmq.listener.simple.acknowledge-mode: manual` config that was
  configured but unused before this change

### Negative consequences / risks

- No dead-letter queue: a message nacked after a genuinely unexpected
  exception (not an ordinary pipeline failure — `IngestPipeline.process()`
  already catches everything internally and always returns a `FAILED`-status
  event, which has its own `/retry` endpoint) is simply discarded, not
  retried or parked for inspection
  - Mitigation: none yet; tracked as a follow-up if this proves to matter in
    practice — kept out of scope here to avoid over-building infrastructure
    for a class of failure that hasn't been observed
- `DocumentReadyEvent` (consumed by `SearchEventConsumer`,
  `@Profile("search")`, still `@ApplicationModuleListener`) has the identical
  latent cross-pod gap and isn't even `@Externalized` — search indexing would
  silently never happen for a genuinely separate search-service pod
  - Mitigation: none yet; explicitly out of scope for this change, tracked as
    a follow-up
- Removing `@ApplicationModuleListener` means these two event types drop out
  of Spring Modulith's event publication registry / `modulith` actuator
  endpoint tracking; observability for them now lives in RabbitMQ's own
  management UI instead
  - Mitigation: acceptable trade — the registry's retry guarantee is
    superseded by RabbitMQ's own at-least-once delivery + manual ack, which
    now also happens to work across process boundaries

## Links

- Related code: `backend/src/main/java/fr/easywork/document/config/RabbitTopologyConfig.java`
- Related code: `backend/src/main/java/fr/easywork/document/config/RabbitTopologyProperties.java`
- Related code: `backend/src/main/java/fr/easywork/ingest/consumer/DocumentUploadedRabbitListener.java`
- Related code: `backend/src/main/java/fr/easywork/document/consumer/IngestCompletedRabbitListener.java`
- Related code: `backend/src/main/java/fr/easywork/ingest/consumer/IngestEventConsumer.java`
- Related code: `backend/src/main/java/fr/easywork/document/service/DocumentService.java`
- Related test: `backend/src/test/java/fr/easywork/ingest/consumer/DocumentUploadedAmqpRoundTripIntegrationTest.java`
- Related: [0002](0002-document-processing-workflow.md) — documented
  `onIngestCompleted` as `@ApplicationModuleListener`; this ADR supersedes
  that specific detail
- CLAUDE.md — repo structure table (doc-service/ingest-worker/search-service
  as separate pods per `SPRING_PROFILES_ACTIVE`)
