# Evidence & Evolution

## Baseline and reporting identifiers

- `C06_BASELINE_COMMIT`: `cd1f93c341afadab1c5176b5303bf100fb8eea7b`
- `CURRENT_REPORTING_COMMIT`: `cd1f93c341afadab1c5176b5303bf100fb8eea7b`

This repository snapshot is the same commit evaluated during the C06 external assessment; the reporting state below is therefore aligned to the same baseline and the current working tree for the corrective changes.

## C06 assessment audit

### F1 — Confirm Reservation preconditions were violated in production code

- Claim ID & Requirement: `F1` / OP-03 Confirm Reservation, REQ-03 and Preconditions in `docs/specification/operations/03-confirm-reservation/OP-03-confirm-reservation.md`; `ReservationService.confirmReservation` must reject inactive vehicles and past-start reservations.
- Reviewer Note: expected `RESOURCE_INACTIVE` for inactive vehicles and a future-start guard before `DRAFT -> CONFIRMED`/`DRAFT -> PENDING_APPROVAL`; observed code skipped both checks.
- Our Verification: `VERIFIED FINDING`
- Disposition: `ACCEPT`
- Evidence & Resolution:
  - Violating path: `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/ReservationService.java:97-120` called `vehicleService.findByIdForUpdate(...)`, then immediately checked conflict and moved to `requestApproval`/`confirm` without an active-vehicle or future-start guard.
  - Domain guard also missing in `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/Reservation.java:64-80` before state mutation.
  - Fix: added guard checks in `confirmReservation` and in `Reservation.confirm(...)` / `Reservation.requestApproval(...)` to reject `RESOURCE_INACTIVE` and `START_MUST_BE_FUTURE` before transition.
  - Files changed: `ReservationService.java`, `Reservation.java`, `ReservationServiceTest.java`.
  - Verification command: `./mvnw.cmd -q -Dtest=ReservationServiceTest test`
  - Result: exit code `0`; the suite passed, including new `rejectsConfirmationForInactiveVehicle` and `rejectsConfirmationWhenStartHasAlreadyPassed` cases.

### F2 — Design drift was real and was corrected by aligning ReservationService with the H2/H1 architecture

- Claim ID & Requirement: `F2` / architecture ownership split in `docs/specification/C04/part-2.md` and the accepted ADR.
- Reviewer Note: the design diagram H2 and sequence diagram H1 explicitly require `ReservationService --> ReservationRules` and a `ConflictOverlapException` branch during collision handling.
- Our Verification: `VERIFIED FINDING -> ACCEPT`
- Disposition: `ACCEPT`
- Evidence & Resolution:
  - Baseline drift existed: `ReservationService` duplicated private validation/expiration logic instead of delegating to `ReservationRules`, and conflict handling threw a generic `IllegalArgumentException("CONFLICT_OVERLAP")` instead of `ConflictOverlapException`.
  - This created a real design-to-implementation mismatch with the accepted architecture in `docs/specification/C04/part-2.md` while also deviating from the domain rules in `ReservationRules` (`MIN_LEAD_TIME = 2 hours`, `calculateReviewDeadline`, `canCancel`, `shouldExpire`).
  - Fix: refactored `ReservationService` to delegate window validation, cancellation checks, and expiration checks to `ReservationRules`, and updated `ensureNoBlockingConflict(...)` to throw `ConflictOverlapException`.
  - Files changed: `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/ReservationService.java`, `src/test/java/cz/vsb/autarepo/autorepo/feature/reservation/ReservationServiceTest.java`.
  - Verification command: `./mvnw.cmd test`
  - Result: exit code `0`; the full Java test suite passed, including the corrected overlap assertion and the layout of the reservation lifecycle checks.

### F3 — Missing evidence and release markers were a real documentation gap

- Claim ID & Requirement: `F3` / C02, C03, and C05 evidence obligations in the assessment form and `docs/evidence-and-evolution.md`.
- Reviewer Note: expected explicit evidence sections and exact commit/tag records for C02/C03/C05.
- Our Verification: `VERIFIED FINDING`
- Disposition: `ACCEPT`
- Evidence & Resolution:
  - The original file `docs/evidence-and-evolution.md` contained only the C01 spike and partial C05 notes; there were no C02/C03 sections and no explicit baseline/reporting commit record.
  - `docs/specification/C04/part-2.md` still had placeholder strings at `...:417` and `...:455`.
  - Fix: populated the baseline/reporting identifiers, added the C06 finding audit, and replaced the placeholder commit/tag strings with the verified baseline value `cd1f93c341afadab1c5176b5303bf100fb8eea7b` in `docs/specification/C04/part-2.md`.

### Supplementary PARTLY items reviewed and dispositioned

- `B1` — baseline identification issue: `ACCEPT` as documentation gap (resolved by this file and the C04 commit/tag update).
- `R4` / `D2` / `D4` — confirm precondition mismatch: `ACCEPT` via `F1`.
- `R5` — missing verification evidence for inactive vehicle/time guard: `ACCEPT` via `F1` + `F3`.
- `A2` / `A3` — architecture mismatch on `ReservationRules`/`ConflictOverlapException`: `ACCEPT` via `F2`.

## Risk assessment

Use consistent IDs across all project documents: `R-01`..`R-05`.

### R-01
- **Risk statement:** Two users may attempt to confirm the same vehicle at the same time, creating a race that can violate BR-02 and produce an overlapping hard or soft lock.
- **Category:** Technical / Quality
- **Initial risk (L/I/priority/rationale):** `L=2, I=3, priority=6` because the exclusive vehicle invariant is central to the project and the current implementation has not yet been proven under PostgreSQL contention.
- **Response strategy and rationale:** `MITIGATE` because the service owns the lifecycle decision and the repository conflict query is designed to block conflicts, but the actual concurrency proof still needs to be executed under simultaneous load.
- **Treatment, owner, due:** Enforce and test transaction-level locking around the vehicle lookup and conflict check before CP2. Owner: `AutoRepo team lead`. Due: `2026-11-15`.
- **Treatment evidence and verification status:** No database contention test has yet been run against the same vehicle under parallel confirm requests. Status: `NOT VERIFIED`.
- **Expected residual risk:** `L=2, I=2, priority=4` after the concurrency test passes and the lock discipline is confirmed.
- **Actual residual risk / NOT VERIFIED:** `NOT VERIFIED` because the parallel PostgreSQL verification has not been executed.
- **Residual risk acceptance, owner, rationale:** Accept only with explicit CP2 sign-off; owner: `AutoRepo team lead`. Rationale: this remains the main outstanding technical risk for the exclusive resource invariant.
- **Monitoring, trigger, contingency, next review:** Monitor all confirm flows for parallel same-vehicle requests; trigger: duplicated confirm attempts on one vehicle; contingency: stricter transaction-level locking and a serializable safety check; next review: `2026-11-15`.

### R-02
- **Risk statement:** Pending approval reservations may expire too early or remain soft-locked too long if the scheduler timing or review-deadline logic drifts.
- **Category:** Technical / Operations
- **Initial risk (L/I/priority/rationale):** `L=2, I=2, priority=4` because stale approval handling affects inventory availability and the timeout boundary is sensitive to scheduler cadence and clock consistency.
- **Response strategy and rationale:** `MITIGATE` because the approval-expiration logic already exists in the service, but the scheduler-driven behavior still needs deterministic runtime proof before release.
- **Treatment, owner, due:** Verify the deadline logic with clock-driven tests and confirm the scheduled expiration path before CP2. Owner: `Reservation domain owner`. Due: `2026-11-15`.
- **Treatment evidence and verification status:** The unit-level expiry path is covered, but the actual scheduler-triggered execution remains unproven. Status: `NOT VERIFIED` for runtime execution.
- **Expected residual risk:** `L=1, I=2, priority=2` after the deadline and scheduler behavior are confirmed under test.
- **Actual residual risk / NOT VERIFIED:** `NOT VERIFIED` because the runtime expiration sweep has not been proven in execution.
- **Residual risk acceptance, owner, rationale:** Accept only with explicit sign-off; owner: `Reservation domain owner`. Rationale: the delay affects operational correctness but is limited to the approval-expiration flow rather than the full reservation lifecycle.
- **Monitoring, trigger, contingency, next review:** Monitor pending approval states and deadline transitions; trigger: a pending reservation remains unreleased or expires unexpectedly; contingency: manual expiration sweep and alerting; next review: `2026-11-15`.

### R-03
- **Risk statement:** The project may depend on a single contributor's local environment knowledge and therefore fail to reproduce the build or demo on a clean machine.
- **Category:** People / Release / Operations
- **Initial risk (L/I/priority/rationale):** `L=2, I=3, priority=6` because local setup dependence can delay the demonstration and release validation even when the code itself is acceptable.
- **Response strategy and rationale:** `MITIGATE` because the repository can document a clean setup and the team can verify it on a second machine to reduce single-person dependence.
- **Treatment, owner, due:** Capture the clean-checkout build/test procedure and verify it on a second machine before CP2. Owner: `Project lead`. Due: `2026-11-12`.
- **Treatment evidence and verification status:** No second-machine verification has yet been recorded. Status: `NOT VERIFIED`.
- **Expected residual risk:** `L=1, I=2, priority=2` once the setup is validated on a second environment.
- **Actual residual risk / NOT VERIFIED:** `NOT VERIFIED` because the clean local setup has not been independently reproduced.
- **Residual risk acceptance, owner, rationale:** Accept only with recorded onboarding evidence; owner: `Project lead`. Rationale: this risk is operationally manageable and should be removed before sign-off rather than accepted permanently.
- **Monitoring, trigger, contingency, next review:** Monitor the ability to reproduce the build on a clean environment; trigger: setup fails outside the local machine; contingency: use a prepared baseline environment; next review: `2026-11-12`.

### R-04
- **Risk statement:** The project may broaden beyond the agreed CP1 boundary by expanding approval-policy work before the core lifecycle is fully verified.
- **Category:** Scope / Requirements
- **Initial risk (L/I/priority/rationale):** `L=1, I=3, priority=3` because the item is not highly likely at present, but it would materially affect the schedule if new approval behaviors are introduced before the current invariant is proven.
- **Response strategy and rationale:** `ACCEPT` because the team can keep the current scope centered on confirmation, approval, rejection, and expiration while deferring broader workflow changes to CP2.
- **Treatment, owner, due:** Keep the CP1 boundary fixed and reject scope expansion unless it is required for release readiness. Owner: `Product owner`. Due: `2026-11-22`.
- **Treatment evidence and verification status:** The repository remains within the agreed CP1 boundary. Status: `PARTLY VERIFIED`, but not a formal gating proof.
- **Expected residual risk:** `L=1, I=2, priority=2` if the scope stays contained.
- **Actual residual risk / NOT VERIFIED:** `NOT VERIFIED` because the project does not formally track a scope gate beyond the current codebase.
- **Residual risk acceptance, owner, rationale:** Accept with explicit scope control; owner: `Product owner`. Rationale: this is a low probability risk that is acceptable if the team holds the CP1 boundary.
- **Monitoring, trigger, contingency, next review:** Monitor feature requests beyond the confirmation/approval/expiration flow; trigger: new approval policy emerges; contingency: defer the change to CP2; next review: `2026-11-22`.

### R-05 — Timezone and clock disparity between host, JVM runtime, and PostgreSQL

- **Cause -> Uncertain Event -> Consequence**: Local development host / Docker container clock mismatch or timezone offset disparity (e.g., local Europe/Prague CET/CEST vs. UTC database session) -> timestamps in interval checks and precondition guards are converted with unexpected offsets -> valid future reservations are erroneously rejected with `START_MUST_BE_FUTURE` or pending approval reservations expire prematurely.
- **Category**: Technical / Quality & Release / Operation
- **Initial Likelihood / Impact / Priority**: `2 / 3 / 6` (HIGH).
  - *Rationale*: Running local JVMs alongside containerized PostgreSQL frequently introduces timezone drift; direct impact on core invariant BR-02 and demonstration reliability.
- **Strategy**: `MITIGATE`.
- **Treatment action**: Uniformly enforce UTC across the stack (set `-Duser.timezone=UTC` / `TimeZone.setDefault("UTC")` in application bootstrap, pin PostgreSQL JDBC URL with `?serverTimezone=UTC`), and inject a deterministic `Clock` bean across all services and scheduler tasks rather than calling `LocalDateTime.now()` unanchored.
- **Owner**: Reservation domain owner
- **Due date**: `2026-11-15`
- **Effectiveness evidence / status**: Verified timezone settings in `application.properties` and integration test asserting UTC timestamp persistence across DB round-trips.
- **Expected Residual**: `1 x 1 = 1` (LOW).
- **Actual Residual**: `NOT VERIFIED` (per rubric rules, remains unverified until end-to-end integration test against PostgreSQL container passes).
- **Acceptance & Trigger**: Team lead accepts with UTC configuration policy; trigger: any unexpected `START_MUST_BE_FUTURE` rejection on future reservations or test failures under non-UTC JVM timezone.

## Evidence summary

- Verified fix for F1: `Resource inactive` and `start time already reached` now fail before state transition.
- Accepted and corrected F2: `ReservationService` now delegates rule evaluation to `ReservationRules` and throws `ConflictOverlapException` on overlap, matching the H2/H1 design.
- Documentation gap F3: closed by adding the evidence audit and baseline commit markers.
- Automated verification: `./mvnw.cmd test` exited successfully with code `0`.
