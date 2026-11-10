# catalogue-service

Events, venues and seat maps for the **Distributed Ticketing Platform**. It owns the `catalogue` database in the platform's shared Postgres ([ADR-028](https://github.com/azneotec/ticketing-docs/blob/master/decisions/ADR-028-shared-postgres-database-per-service.md)).

Status (session 006): domain, use cases and persistence are done. REST/OpenAPI, the admin API, the gRPC server and OpenTelemetry arrive in session 007. The only HTTP endpoint so far is `/actuator/health`.

## Modules (strict hexagonal, [ADR-004](https://github.com/azneotec/ticketing-docs/blob/master/decisions/ADR-004-strict-hexagonal-archunit.md))

| Module | Package | Plugin | Holds |
|---|---|---|---|
| `domain` | `…catalogue.domain` | `ticketing.domain-conventions` | `Event` aggregate, sealed `EventStatus`, domain events, `Venue`, `SeatMap` records, typed ids. Pure Java: no libraries at all |
| `application` | `…catalogue.application` | `ticketing.java-conventions` | Inbound ports (`CreateVenue`, `CreateEvent`, `PublishEvent`, `GetEvent`, `ListEvents`), outbound ports (`EventRepository`, `VenueRepository`), services. Plain Java |
| `adapter-persistence` | `…catalogue.adapter.persistence` | `ticketing.spring-boot-conventions` (BOM only, no boot jar) | Flyway migrations, JPA entities, Spring Data repositories, mappers, port implementations |
| `bootstrap` | `…catalogue` | `ticketing.spring-boot-conventions` | `main()`, wiring of the plain-Java use cases, `application.properties`, the ArchUnit test |

Dependencies point inwards only: adapter → application → domain. ArchUnit (`bootstrap/src/test/.../ArchitectureTest.java`) and the domain dependency guard enforce this in `./gradlew build`.

## Run it

Needs JDK 25, and Docker (OrbStack) running for the tests.

```bash
./gradlew build                          # all tests (Testcontainers starts its own Postgres), coverage, ArchUnit, Spotless
./gradlew spotlessApply                  # fix formatting
```

Against the local platform (`ticketing-platform`):

```bash
cd ../ticketing-platform && make up PROFILES="core"   # Postgres + otel-lgtm (~0.6 GB)
cd ../catalogue-service && ./gradlew :bootstrap:bootRun   # -Xmx256m, port 8080
curl localhost:8080/actuator/health
```

On start-up, Flyway applies `adapter-persistence/src/main/resources/db/migration` to the `catalogue` database. Hibernate then only **validates** the entities against that schema (`ddl-auto=validate`).

| Environment variable | Default (matches `ticketing-platform/.env.example`) |
|---|---|
| `CATALOGUE_DB_HOST` / `CATALOGUE_DB_PORT` | `localhost` / `5432` |
| `CATALOGUE_DB_USER` / `CATALOGUE_DB_PASSWORD` | `catalogue` / `catalogue-local-dev` |
| `PORT` | `8080` |

## Persistence notes
- **Seat map in JSONB:** one `seat_map` document per event (`{"sections":[{"name":…,"rows":[{"label":…,"seats":[…]}]}]}`), indexed with GIN `jsonb_path_ops` for `@>` containment queries ([ADR-006](https://github.com/azneotec/ticketing-docs/blob/master/decisions/ADR-006-jsonb-over-mongodb.md)).
- **JPA entities are not the domain:** `EventEntity`/`VenueEntity` live only in the adapter, and `EventMapper` converts in both directions ([ADR-031](https://github.com/azneotec/ticketing-docs/blob/master/decisions/ADR-031-spring-data-jpa-separate-entities.md)).
- **Optimistic locking:** `event.version` (`@Version`). The domain `Event` carries the version it was read with. `save` rejects a stale copy with `ConcurrentUpdateException`, and Hibernate's `where version = ?` catches races.
- **No N+1 in lists:** `ListEvents` uses one JPQL constructor query joined with the venue, plus one count query. `NPlusOneTest` counts the statements.
- **Migrations are immutable:** never edit an applied `V<n>__*.sql`. Add a new one.

## Versioning
Same tag-based scheme as the other repos (`gradle/version.gradle.kts`): exactly on tag `vX.Y.Z` with a clean tree it is `X.Y.Z`; anything else is `X.Y.(Z+1)-SNAPSHOT`; with no tag yet it is `0.1.0-SNAPSHOT`. Conventional Commits, trunk-based on `master`.
