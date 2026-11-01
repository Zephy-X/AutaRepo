# Technical Defense Brief — Confirm Reservation

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
- `VehicleService.findByIdForUpdate` obtains the lock needed for contention control.
- `NotificationServiceStub` emits post-commit side effects only after state mutation.

### TO-BE (current working design)

- Lifecycle authority remains `ReservationService`.
- `Reservation` aggregate enforces local invariants for state and resource activity.
- `ReservationRepository` remains the persistence boundary with the overlap query.
- Scheduler reuses the same lifecycle service for expiration (`expireStaleReservations`).

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

## 5) C06 finding defense

### Accepted finding

`F1` was correct: the baseline code did not enforce the active-vehicle and future-start preconditions before confirmation. The fix is in `ReservationService.confirmReservation(...)` and the `Reservation` aggregate guards.

### Accepted and reconciled finding

`F2` was a genuine design drift: the code duplicated the rule logic in `ReservationService` and threw a generic overlap exception instead of the domain-specific `ConflictOverlapException` required by the H2/H1 design. The fix aligned the implementation with the accepted architecture by delegating validation and expiry/cancel decisions to `ReservationRules` and by throwing `ConflictOverlapException` in `ensureNoBlockingConflict(...)` when a blocking overlap is detected.

### Documentation gap fix

`F3` was valid and is now closed: baseline + reporting commit identifiers and evidence sections are present in `docs/evidence-and-evolution.md`, and the placeholder C04 tags were replaced with the verified baseline commit.

## Closing statement

The implementation now meets the invariant required by `OP-03`: no inactive or already-started resource can be confirmed, and no conflicting blocking reservation enters the system without the overlap guard. This is the technical basis for the defense.
