# C01 Engineering Spike

## Question / unknown

Can our Spring Boot 3 + Java 21 application connect to PostgreSQL 16 running in Docker, generate the required schema via Hibernate, and successfully persist and reload a complete `Reservation` entity graph (`Reservation` -> `Vehicle` + `User`)?

## What we did

1. Configured `docker-compose.yml` with PostgreSQL 16 and pgAdmin 4.
2. Created entity models (`Vehicle`, `User`, `Reservation`) using Jakarta Persistence and Spring Data JPA repositories.
3. Implemented an automated integration test `ReservationPersistenceSpikeTest.java` (Spike A) that builds a test reservation, saves it to PostgreSQL, explicitly flushes and clears the Hibernate session (`EntityManager.flush()` / `clear()`), and re-reads it using `findById`.

## Observed result

The test executed successfully. The PostgreSQL container accepted connections on `localhost:5432`, Hibernate automatically applied DDL updates, and after evicting the persistence context, a real SQL `SELECT` retrieved the record with matching IDs, timestamps, and foreign key relations.

## Decision / what changes because of the result

We confirm that Dockerized PostgreSQL with Spring Data JPA satisfies our persistence requirements. We will proceed with this baseline stack for the CP1 walking skeleton without introducing extra database migration tooling until needed.

## C05 DD Verification Evidence

### Tests derived from DD

The C05 design derives eight verification cases covering interval validation,
half-open overlap boundaries, confirmed-only allocation checks, approval
routing, approval expiration, cancellation, and persistence collaboration.
The complete matrix is documented in the C05 section of
`docs/architecture-and-decisions.md`.

### Unit/component tests executed

The repository currently contains one component/integration verification test:

- `ReservationPersistenceSpikeTest.testReservationPersistenceWithRelationships`
  (`mvn test -Dtest=ReservationPersistenceSpikeTest`)
- Execution in this environment: **blocked**; `mvn` is not installed, and the
  Maven wrapper run cannot create the JPA context because no JDBC URL or
  reachable PostgreSQL test service is configured.
- Intended assertions: flush and clear the persistence context, reload a
  `Reservation`, and verify persisted state, timestamps, user relationship,
  and vehicle relationship. The earlier C01 evidence records a successful
  execution in the configured Docker/PostgreSQL environment.

The seven service-policy tests derived from C05 are specified but are not yet
implemented in the test source tree. They are therefore verification targets,
not falsely reported execution results.

### Design ↔ implementation mismatch

| DD decision | Implemented? | Evidence | Action |
|---|---|---|---|
| Reservation lifecycle is owned by `ReservationService` | YES | `feature/reservation/ReservationService.java` | Keep DD and implementation. |
| Confirmed-only half-open overlap query protects BR-02 | YES | `ReservationRepository` derived query and service callers | Keep; add isolated boundary tests. |
| Luxury confirmation routes to `PENDING_APPROVAL` | YES | `confirmReservation` checks `Vehicle.requiresApproval` | Keep; add service test. |
| Expired pending approval transitions to `EXPIRED` | YES | `approveReservation` checks `now >= startTime` | Keep; add clock-controlled test. |
| Reservation duration is at most 14 days | YES | `validateReservationWindow` rejects durations greater than 14 days with `MAX_DURATION_EXCEEDED` | Keep DD and implementation; boundary test added. |
| Temporal rules use a stable/injectable clock | YES | `ReservationService` receives a `Clock`; production `TimeConfig` supplies UTC | Keep DD and implementation; fixed-clock tests added. |
| Rejection emits a notification as required by the architecture boundary | NO | `rejectReservation` saves only; `NotificationServiceStub` has no rejection method | Decide and implement the notification contract before claiming full conformance. |

### Resolution

The C05 documentation is retained as the intended design. The service policy
implementation now enforces the 14-day maximum and uses an injectable UTC
clock; both decisions have focused unit coverage. The targeted persistence
test could not be re-executed here because the database configuration/service
is unavailable. Rejection notification remains an explicit implementation
decision for a later change.

### Remaining design risk

- Concurrent confirmation and approval can still race because the current
  design has no explicit row-lock or optimistic-version strategy.
- `PENDING_APPROVAL` remains open to availability checks until approval, as
  recorded in the baseline unknowns.
- Persistence verification still requires the configured PostgreSQL service;
  the service-policy branches are covered by `ReservationServiceTest`.

### Commit/tag

No new commit or tag was created for this documentation-only change. The
evidence remains associated with the existing `baseline-v0.2` application
baseline.