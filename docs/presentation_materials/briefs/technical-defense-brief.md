# Technical Defense Brief — Confirm Reservation

**Current reporting commit:** `1012afad99641aac84f8fb674f77d1532ccb991d` (`feat: add C07 (#14)`)
**Verification update:** the full Maven test suite passes when Docker Compose is running, including PostgreSQL-backed persistence checks.

## 1) Scope & invariant

We defend the `OP-03 Confirm Reservation` path and the core invariant `BR-02`: two blocking reservations for the same vehicle must never overlap.

- Scope: `ReservationService.confirmReservation(Long id)`
- Business invariant: `max(A.start, B.start) < min(A.end, B.end)` must not evaluate true for any pair of records in `CONFIRMED` or `PENDING_APPROVAL` on the same `Vehicle`.
- Source of truth: `docs/specification/operations/03-confirm-reservation/OP-03-confirm-reservation.md` and `docs/business-rules.md`.
- Key preconditions enforced: reservation exists, state is `DRAFT`, vehicle is active, and `currentTime < reservation.startTime`.

## 2) Architecture: AS-IS vs TO-BE

### AS-IS (accepted baseline)

- `ReservationViewController` triggers commands into the service layer.
- `ReservationService` owns confirmation, approval, rejection, expiration, and cancellation decisions.
- `ReservationRepository` exposes a conflict query (`hasStateConflict`) and persists state.
- `VehicleService.findByIdForUpdate` obtains the vehicle row lock used by the current contention-control design.
- `NotificationServiceStub` emits post-commit side effects only after state mutation.

### TO-BE (current working design)

- Lifecycle authority remains `ReservationService`.
- `Reservation` aggregate enforces local invariants for state and resource activity.
- `ReservationRepository` remains the persistence boundary with the overlap query.
- Scheduler reuses the same lifecycle service for expiration (`expireStaleReservations`).

The current implementation is functionally verified on the local Docker/PostgreSQL
environment, but the concurrency claim is not yet fully evidenced: there is no
dedicated parallel-confirm stress test that demonstrates the outcome under
simultaneous PostgreSQL transactions.

### Ownership boundary

- Decision ownership: `ReservationService`
- Persistence ownership: `ReservationRepository`
- Notification ownership: `NotificationServiceStub`
- Controller ownership: request translation only, no lifecycle policy

Relevant ADR: `ADR-03 — Ownership of confirmation/lifecycle transition decisions under concurrency` in `docs/specification/C04/part-2.md`.

## 3) Detailed design

### Call path

`ReservationService.confirmReservation(id)`:
1. Load reservation by id.
2. Require `DRAFT` state.
3. Lock the vehicle by `findByIdForUpdate`.
4. Validate `vehicle.isActive()` and `now < startTime`.
5. Run `hasStateConflict(vehicleId, start, end, ignoreReservationId)`.
6. If approval is required, call `reservation.requestApproval(now)`; else `reservation.confirm(now)`.
7. Save and flush.
8. Notify the external boundary.

### Key methods and failure handling

- `Reservation.confirm(LocalDateTime now)`
  - Guards: `requireState(DRAFT)`, `assertVehicleActive()`, `assertStartInFuture(now)`.
  - Failure: `INVALID_STATE_TRANSITION`, `RESOURCE_INACTIVE`, `START_MUST_BE_FUTURE`.
- `Reservation.requestApproval(LocalDateTime now)`
  - Same guard logic.
  - Sets `submittedAt` and calculates `reviewDeadline` using the same half-open policy.
- `ReservationService.ensureNoBlockingConflict(...)`
  - Throws `IllegalArgumentException("CONFLICT_OVERLAP")` when a blocking overlap exists.
- `ReservationService.approveReservation(...)`
  - Expiration path remains `APPROVAL_WINDOW_EXPIRED` when `now >= reviewDeadline || now >= startTime`.

## 4) Executable verification

### Positive test

Command:

`./mvnw.cmd -q -Dtest=ReservationServiceTest#routesApprovalRequiredVehicleToPendingApproval test`

Expected oracle:
- existing draft -> becomes `PENDING_APPROVAL`
- `notificationService.notifyReservationPendingApproval` called once
- no `CONFIRMED` notification sent

Observed result:
- test passes and the reservation transitions as expected.

### Negative / boundary test

Command:

`./mvnw.cmd -q -Dtest=ReservationServiceTest#rejectsConfirmationForInactiveVehicle test`

Expected oracle:
- `IllegalStateException` with message `RESOURCE_INACTIVE`
- reservation remains in `DRAFT`
- no notification is fired

Observed result:
- test passes; the guard blocks the invalid transition.

### Full targeted suite

Command:

`./mvnw.cmd -q -Dtest=ReservationServiceTest test`

Result:
- exit code `0`
- 12 targeted tests pass, validating the request/approval/expiration/cancel paths and the new guard conditions.

### Full repository test suite with Docker/PostgreSQL

Command:

`./mvnw.cmd -q test`

Environment and result:

- Docker Compose was running with `local_postgres` healthy on port `5432`.
- The PostgreSQL persistence test connected to PostgreSQL `16.15` and Hibernate selected `PostgreSQLDialect`.
- The full Maven test suite exited with code `0`.
- The earlier dialect/metadata failure was environmental: the PostgreSQL container was stopped.

This result proves the repository's current integration path works with the
required database available. It does not by itself prove a race-free outcome
for two simultaneous confirmation transactions, nor does it replace a
dedicated runtime scheduler-expiration test.

## 5) C06 finding defense

### Accepted finding

`F1` was correct: the baseline code did not enforce the active-vehicle and future-start preconditions before confirmation. The fix is in `ReservationService.confirmReservation(...)` and the `Reservation` aggregate guards.

### Accepted and reconciled finding

`F2` was a genuine design drift: the code duplicated the rule logic in `ReservationService` and threw a generic overlap exception instead of the domain-specific `ConflictOverlapException` required by the H2/H1 design. The fix aligned the implementation with the accepted architecture by delegating validation and expiry/cancel decisions to `ReservationRules` and by throwing `ConflictOverlapException` in `ensureNoBlockingConflict(...)` when a blocking overlap is detected.

### Documentation gap fix

`F3` was valid and is now closed: baseline + reporting commit identifiers and evidence sections are present in `docs/evidence-and-evolution.md`, and the placeholder C04 tags were replaced with the verified baseline commit.

## Closing statement

The implementation now enforces the `OP-03` guards and overlap decision path,
and the complete test suite passes with PostgreSQL running in Docker. The
remaining AMBER items are evidence gaps, not the previously accepted C06
defects: dedicated PostgreSQL contention proof, scheduler-driven expiration
proof, and clean-environment reproducibility.
