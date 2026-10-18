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

| DD element / decision | What must hold? | Test level | Test case |
|---|---|---|---|
| Window contract | Equal start/end is rejected. | Unit | `rejectsZeroLengthInterval` |
| Future-start rule | A start at or before the fixed clock is rejected. | Unit | `rejectsPastStart` |
| Maximum duration | More than 14 days fails, exactly 14 days succeeds. | Unit | `rejectsDurationLongerThanFourteenDays`, `acceptsDurationOfExactlyFourteenDays` |
| Half-open overlap | Adjacent intervals remain available. | Unit | `adjacentIntervalsRemainAvailable` |
| Conflict branch | A strict blocking overlap fails confirmation with `CONFLICT_OVERLAP`. | Unit | `overlappingConfirmedIntervalRaisesConflict` |
| Approval routing | Approval-required vehicles enter `PENDING_APPROVAL`, not `CONFIRMED`. | Unit | `routesApprovalRequiredVehicleToPendingApproval` |
| Expiration boundary | Approval at `startTime` expires and raises `APPROVAL_WINDOW_EXPIRED`. | Unit | `approvalExpiresAtStartTime` |
| Cancellation transition | Eligible reservation becomes `CANCELLED` and emits cancellation notification. | Unit | `cancelsBeforeStartAndNotifies` |
| Rejection transition | Pending reservation becomes `REJECTED` and emits rejection notification. | Unit | `rejectsPendingReservationAndNotifies` |

### Unit/component tests executed

- `.\mvnw.cmd -q -Dtest=ReservationServiceTest test` — passed; 10 tests.
- `.\mvnw.cmd -q test` — passed; focused unit tests plus existing lifecycle integration, persistence, and domain tests.
- The fixed `Clock` in `ReservationServiceTest` verifies temporal behavior without relying on wall-clock timing.
- `ReservationLifecycleIntegrationTest` verifies pending approval soft-lock behavior, approval promotion, and stale expiration against JPA.

### Design ↔ implementation mismatch

| DD decision | Implemented? | Evidence | Resolution |
|---|---|---|---|
| UTC `Clock` is injected into lifecycle logic. | YES | `config/TimeConfig`, `ReservationService`, fixed-clock unit tests | Keep. |
| Half-open overlap query excludes adjacent ranges. | YES | `ReservationRepository.hasStateConflict`, unit and integration tests | Keep. |
| `CONFIRMED` and `PENDING_APPROVAL` block availability. | YES | Repository JPQL and integration test | Keep; this follows the accepted v0.3 project rules. |
| Approval-required vehicles route through `PENDING_APPROVAL`. | YES | `confirmReservation`, `routesApprovalRequiredVehicleToPendingApproval` | Keep. |
| Domain rules are exclusively owned by `ReservationService`. | PARTLY | `Reservation` and `ReservationRules` still implement cancellation/state guards | Decide whether to preserve aggregate guards or move all guards into the service in a follow-up refactor. |
| A documented C05 artifact and release marker exist. | YES / NO | This section and `architecture-and-decisions.md` now contain C05; no dedicated C05 tag exists yet | Create a tag after review/acceptance. |

### Remaining design risk

The primary unresolved risk is concurrency: the vehicle row is pessimistically locked before confirmation, but the overlap query itself must be validated under the production PostgreSQL transaction/isolation profile for simultaneous confirmations. Authorization of approver actions is also outside this service slice and must be enforced at the controller/security boundary.

### Commit/tag

- Existing repository history contains commits labelled `Feat/C05`, but those commits only changed `README.md`.
- No dedicated C05 release tag was present during this verification.
- Recommended release marker after review: `c05-detailed-design`.