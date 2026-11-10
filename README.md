# catalogue-service

Events, venues and seat maps for the **Distributed Ticketing Platform**. It owns the `catalogue` database in the platform's shared Postgres ([ADR-028](https://github.com/azneotec/ticketing-docs/blob/master/decisions/ADR-028-shared-postgres-database-per-service.md)).

It serves:
- **REST** on port 8080: a public read API for the web shell, and an admin API for organisers that only accepts calls from this machine.
- **gRPC** on port 9090: read access for other services ([ADR-010](https://github.com/azneotec/ticketing-docs/blob/master/decisions/ADR-010-rest-edge-grpc-inside.md)).
- **Telemetry**: traces, metrics and logs over OTLP to the local Grafana stack.

## Modules (strict hexagonal, [ADR-004](https://github.com/azneotec/ticketing-docs/blob/master/decisions/ADR-004-strict-hexagonal-archunit.md))

| Module | Package | Plugin | Holds |
|---|---|---|---|
| `domain` | `…catalogue.domain` | `ticketing.domain-conventions` | `Event` aggregate, sealed `EventStatus`, domain events, `Venue`, `SeatMap` records, typed ids. Pure Java: no libraries at all |
| `application` | `…catalogue.application` | `ticketing.java-conventions` | Inbound ports (`CreateVenue`, `GetVenue`, `CreateEvent`, `PublishEvent`, `GetEvent`, `ListEvents`), outbound ports (`EventRepository`, `VenueRepository`), services. Plain Java |
| `adapter-persistence` | `…catalogue.adapter.persistence` | `ticketing.spring-boot-conventions` (BOM only, no boot jar) | Flyway migrations, JPA entities, Spring Data repositories, mappers, port implementations |
| `adapter-rest` | `…catalogue.adapter.rest` | same | Spring MVC controllers, DTO records, Problem Details mapping, localhost-only admin guard, CORS, OpenAPI |
| `adapter-grpc` | `…catalogue.adapter.grpc` | same | `ticketing.catalogue.v1.CatalogueService` from `ticketing-contracts-grpc` 0.1.1 |
| `bootstrap` | `…catalogue` | `ticketing.spring-boot-conventions` | `main()`, wiring (use cases wrapped in observations), configuration, logging, the ArchUnit test, integration tests |

Dependencies point inwards only: adapter → application → domain, and adapters never use each other. ArchUnit (`bootstrap/src/test/.../ArchitectureTest.java`) and the domain dependency guard enforce this in `./gradlew build`.

## Run it

Needs JDK 25, and Docker (OrbStack) running for the tests.

```bash
./gradlew build            # all tests (Testcontainers starts its own Postgres), 80% coverage, ArchUnit, Spotless
./gradlew spotlessApply    # fix formatting
```

Against the local platform (`ticketing-platform`):

```bash
cd ../ticketing-platform && make up PROFILES="core"       # Postgres + otel-lgtm
cd ../catalogue-service && ./gradlew :bootstrap:bootRun   # -Xmx256m; HTTP 8080, gRPC 9090
curl localhost:8080/actuator/health
```

When the service starts, Flyway applies `adapter-persistence/src/main/resources/db/migration` to the `catalogue` database. Hibernate then only **validates** the entities against that schema (`ddl-auto=validate`).

Measured on the M1 with `-Xmx256m`: starts in ~4.5 s. RSS (resident memory) is ~95 MB idle and ~280 MB after traffic: heap, plus Netty for gRPC, plus the OpenTelemetry SDK.

| Environment variable | Default |
|---|---|
| `CATALOGUE_DB_HOST` / `CATALOGUE_DB_PORT` | `localhost` / `5432` |
| `CATALOGUE_DB_USER` / `CATALOGUE_DB_PASSWORD` | `catalogue` / `catalogue-local-dev` (matches `ticketing-platform/.env.example`) |
| `PORT` | `8080` (HTTP) |
| `GRPC_PORT` | `9090` |
| `CATALOGUE_CORS_ALLOWED_ORIGINS` | `http://localhost:5173` (comma-separated) |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://localhost:4318` (OTLP/HTTP; `/v1/traces`, `/v1/metrics`, `/v1/logs` are appended) |
| `TRACING_SAMPLING_PROBABILITY` | `1.0` (every request) |
| `DEPLOYMENT_ENVIRONMENT` | `local` |

## REST API

The spec is committed at [`docs/openapi.yaml`](docs/openapi.yaml). Swagger UI runs at http://localhost:8080/swagger-ui.html, and the live spec is at `/v3/api-docs.yaml`. `OpenApiSpecTest` fails when the committed file no longer matches the code. To regenerate it:
```bash
./gradlew :bootstrap:test --tests '*OpenApiSpecTest' -PupdateOpenApi
```

| Method + path | Who | Result |
|---|---|---|
| `GET /events?status=PUBLISHED,CANCELLED&page=0&size=20` | anyone | Page of summaries, soonest first. Default `PUBLISHED`. `DRAFT` → 400. `size` 1–100 |
| `GET /events/{id}` | anyone | Event with seat map and `ETag: "<version>"`. Drafts → 404. `If-None-Match` → 304 |
| `POST /admin/venues` | localhost | 201 + `Location` |
| `GET /admin/venues/{id}` | localhost | The venue |
| `POST /admin/events` | localhost | Creates a draft. 201 + `Location` + `ETag: "0"` |
| `GET /admin/events/{id}` | localhost | Any status, with the current `ETag` |
| `POST /admin/events/{id}/publish` | localhost | Requires `If-Match`. 200 + new `ETag` |

**Errors** are Problem Details (`application/problem+json`, RFC 9457), with a stable `type` such as `urn:ticketing:problem:stale-version` ([ADR-032](https://github.com/azneotec/ticketing-docs/blob/master/decisions/ADR-032-problem-details-and-etag-if-match.md)):

| Status | When |
|---|---|
| 400 | Invalid value or malformed request; validation errors list every field in `errors` |
| 403 | Admin call from another machine |
| 404 | No such event or venue |
| 409 | The event's state forbids the change, e.g. publishing twice |
| 412 | `If-Match` is stale: someone changed the event since you read it. Reload and retry |
| 428 | `If-Match` missing (or `*`) on a state-changing admin call |

**Admin API is localhost only** until Keycloak arrives in Phase 2 ([ADR-033](https://github.com/azneotec/ticketing-docs/blob/master/decisions/ADR-033-localhost-only-admin-api.md)):
- An interceptor on `/admin/**` accepts loopback callers only (`127.0.0.0/8`, `::1`) and answers 403 to everyone else.
- It trusts the TCP peer address and ignores `X-Forwarded-For`.
- `/admin` has no CORS, so web pages can't call it from a browser.

**CORS:** `GET`/`HEAD` on `/events/**` from `CATALOGUE_CORS_ALLOWED_ORIGINS`; `ETag` is exposed to scripts.

An admin flow with `curl`:
```bash
VENUE=$(curl -s -X POST localhost:8080/admin/venues -H 'Content-Type: application/json' \
  -d '{"name":"Royal Hall","city":"London","zone":"Europe/London"}' | jq -r .id)
EVENT=$(curl -s -X POST localhost:8080/admin/events -H 'Content-Type: application/json' \
  -d "{\"title\":\"Concert\",\"venueId\":\"$VENUE\",\"startsAt\":\"2026-12-20T19:30:00Z\",
       \"seatMap\":{\"sections\":[{\"name\":\"Stalls\",\"rows\":[{\"label\":\"A\",\"seats\":[\"1\",\"2\"]}]}]}}" | jq -r .id)
curl -i -X POST localhost:8080/admin/events/$EVENT/publish -H 'If-Match: "0"'   # 200, ETag "1"
curl -i -X POST localhost:8080/admin/events/$EVENT/publish -H 'If-Match: "0"'   # 412, stale
curl -s localhost:8080/events | jq
```

## gRPC API

`ticketing.catalogue.v1.CatalogueService` on port 9090 (plaintext locally) serves:
- `GetEvent`: any status, because callers are trusted services.
- `ListEvents`: published events, soonest first. `page_size` 0 means 20 and is capped at 100. `page_token` is opaque.

Server reflection and the standard `grpc.health.v1.Health` service are on, so `grpcurl` needs no `.proto` files:
```bash
grpcurl -plaintext localhost:9090 list
grpcurl -plaintext -d '{"event_id":"<uuid>"}' localhost:9090 ticketing.catalogue.v1.CatalogueService/GetEvent
grpcurl -plaintext -d '{"page_size":5}' localhost:9090 ticketing.catalogue.v1.CatalogueService/ListEvents
grpcurl -plaintext localhost:9090 grpc.health.v1.Health/Check
```

Error codes:

| Code | When |
|---|---|
| `NOT_FOUND` | No such event or venue |
| `INVALID_ARGUMENT` | Malformed id or page token, negative page size, invalid value |
| `FAILED_PRECONDITION` | Illegal state transition (no write RPCs yet) |
| `ABORTED` | Version conflict |
| `INTERNAL` | Anything else; the message is withheld and logged on the server |

**Deadlines:** set one on every call (`-max-time` in `grpcurl`). When it passes the client gets `DEADLINE_EXCEEDED`. The server skips calls that are already expired and drops replies nobody is waiting for. The gRPC server runs on Spring Boot 4.1's built-in support ([ADR-034](https://github.com/azneotec/ticketing-docs/blob/master/decisions/ADR-034-grpc-server-and-opentelemetry-wiring.md)).

## Observability ([ADR-007](https://github.com/azneotec/ticketing-docs/blob/master/decisions/ADR-007-observability-day-one.md), [ADR-034](https://github.com/azneotec/ticketing-docs/blob/master/decisions/ADR-034-grpc-server-and-opentelemetry-wiring.md))

Spring Boot's own OpenTelemetry support, no Java agent. Micrometer Observation instruments the code; the OpenTelemetry SDK exports over OTLP/HTTP to `otel-lgtm`.
- **Traces:** each request gives one trace:
  - REST: `http <method> <path>` → use case (`PublishEvent`, …) → `connection` / `query` (SQL).
  - gRPC: `ticketing.catalogue.v1.CatalogueService/GetEvent` → use case → SQL.
  - Resource attributes: `service.name=catalogue-service`, `service.version`, `deployment.environment.name=local`.
- **Logs:** console lines carry `[traceId-spanId]`. The same records go to Loki through the OpenTelemetry Logback appender. Admin changes are logged at INFO.
- **Metrics** (every 15 s):
  - `http_server_requests_milliseconds_*`, `grpc_server_*`, `catalogue_use_case_milliseconds_*` (tag `use_case`), JDBC and JVM metrics.
  - Rate, errors and duration (RED) per endpoint, RPC and use case.

In Grafana (http://localhost:3000) → Explore:

| Signal | Data source | Query |
|---|---|---|
| Traces | Tempo | `{resource.service.name="catalogue-service"}`, or paste a trace id from a log line |
| Logs of one trace | Loki | `{service_name="catalogue-service"} \| trace_id="<trace id>"` |
| HTTP requests | Prometheus | `sum by (method, uri, status) (http_server_requests_milliseconds_count{service_name="catalogue-service"})` |
| Use-case latency p95 | Prometheus | `histogram_quantile(0.95, sum by (le, use_case) (rate(catalogue_use_case_milliseconds_bucket[5m])))` |

Tests never export: the integration tests switch OTLP off, and `TracingTest` checks the shape of a trace with an in-memory exporter.

## Persistence notes
- **Seat map in JSONB:** one `seat_map` document per event (`{"sections":[{"name":…,"rows":[{"label":…,"seats":[…]}]}]}`), indexed with GIN `jsonb_path_ops` for `@>` containment queries ([ADR-006](https://github.com/azneotec/ticketing-docs/blob/master/decisions/ADR-006-jsonb-over-mongodb.md)).
- **JPA entities are not the domain:** `EventEntity`/`VenueEntity` live only in the adapter, and `EventMapper` converts in both directions ([ADR-031](https://github.com/azneotec/ticketing-docs/blob/master/decisions/ADR-031-spring-data-jpa-separate-entities.md)).
- **Optimistic locking:**
  - `event.version` uses `@Version`.
  - `PublishEvent` compares the caller's expected version (the HTTP `If-Match`) with the stored one.
  - `save` rejects a stale copy with `ConcurrentUpdateException`, and Hibernate's `where version = ?` catches races.
- **No N+1 in lists:** `ListEvents` uses one JPQL constructor query joined with the venue, plus one count query. `NPlusOneTest` counts the statements.
- **Migrations are immutable:** never edit an applied `V<n>__*.sql`. Add a new one.

## Versioning and releasing
Same tag-based scheme as the other repos (`gradle/version.gradle.kts`):
- Exactly on tag `vX.Y.Z` with a clean tree, the version is `X.Y.Z`.
- Anything else is `X.Y.(Z+1)-SNAPSHOT`.
- With no tag yet, it is `0.1.0-SNAPSHOT`.

The version also becomes `service.version` in telemetry.

To release:
1. `master` is clean and `./gradlew build` passes.
2. `git tag -a vX.Y.Z -m "vX.Y.Z"`, then `./gradlew build` again to confirm the version is `X.Y.Z`.
3. `git push && git push origin vX.Y.Z`

Conventional Commits, trunk-based on `master`.
