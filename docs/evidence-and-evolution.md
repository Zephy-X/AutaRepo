# Evidence & Evolution

## Baseline and reporting identifiers

- `C06_BASELINE_COMMIT`: `cd1f93c341afadab1c5176b5303bf100fb8eea7b`
- `CURRENT_REPORTING_COMMIT`: `cd1f93c341afadab1c5176b5303bf100fb8eea7b`
- `C08_BASELINE_COMMIT`: `c8db481f5913753e2e86abb5791ebbb875e78963` (see [C08 section](#c08--before-baseline-and-test-evidence))

This repository snapshot is the same commit evaluated during the C06 external assessment; the reporting state below is therefore aligned to the same baseline and the current working tree for the corrective changes.

## C02/C03/C05 evidence index

GitHub Issues establish the tracking history, but an issue being closed does not by itself prove that every mandatory output was produced. The table below separates repository artifacts, tracking records, implementation commits, and verification evidence. No repository tag for these deliverables was found; exact commit and pull-request links are therefore used instead.

| Course / deliverable | Requirement or output | Evidence type | Exact evidence | Status | Notes / remaining gap |
|---|---|---|---|---|---|
| C02 | Baseline v0.2 approval-workflow change, affected slices, rules, verification examples | Repository document; issue; PR; commit | [`docs/baseline-v0.2-change-record.md`](baseline-v0.2-change-record.md); [Issue #6](https://github.com/Zephy-X/AutaRepo/issues/6); [PR #7](https://github.com/Zephy-X/AutaRepo/pull/7); [commit `70f0d33`](https://github.com/Zephy-X/AutaRepo/commit/70f0d33d20c7fd231d27b79fd8e175247d3c9049) | VERIFIED | The baseline record contains the change impact and executed verification examples. No separate repository tag was found. |
| C03 Part A | AS-IS mapping of one scenario, implementation elements, state/rules, dependencies, structural evidence | Repository directory; issue; PR; commit | [`docs/specification/C03/`](specification/C03/); [Issue #8](https://github.com/Zephy-X/AutaRepo/issues/8); [PR #9](https://github.com/Zephy-X/AutaRepo/pull/9); [commit `de27a59`](https://github.com/Zephy-X/AutaRepo/commit/de27a59c6395e2625ceb4a4b7d6be4cc58241b27) | VERIFIED | The C03 Part A artifact set is present and linked to its tracking issue and merged PR. |
| C03 Part B / architecture | Architecture drivers, domain model, views, runtime verification, and design-to-code delta | Repository document; issue; PR; commit | [`docs/specification/C04/part-2.md`](specification/C04/part-2.md); [Issue #12](https://github.com/Zephy-X/AutaRepo/issues/12); [PR #11](https://github.com/Zephy-X/AutaRepo/pull/11); [commit `1e22e48`](https://github.com/Zephy-X/AutaRepo/commit/1e22e4840f4474d62b8f7716acde577b7fdb03aa) | PARTLY VERIFIED | The document is headed “C03 — Architecture Evidence” but is stored under `C04`; the issue requested centralization in this evidence file, which was not previously true. |
| C05 | Detailed design of the reservation lifecycle, contracts, responsibilities, structural/dynamic design, and test derivation | Repository document; issue; PR; implementation/test claim | [`docs/architecture-and-decisions.md#c05-detailed-design`](architecture-and-decisions.md#c05-detailed-design); [Issue #13](https://github.com/Zephy-X/AutaRepo/issues/13); [PR #11](https://github.com/Zephy-X/AutaRepo/pull/11) | PARTLY VERIFIED | The C05 design section and tracking records exist, and PR #11 claims unit and PostgreSQL verification. A separate C05 evidence index and independently reproduced test record were not identifiable before this correction. |

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

### F3 — Evidence traceability was incomplete and the original claim was overbroad

- Claim ID & Requirement: `F3` / traceability of C02, C03, and C05 mandatory outputs in the assessment form and project evidence.
- Reviewer Note: expected explicit evidence sections and exact commit/tag records for C02/C03/C05.
- Our Verification: `VERIFIED FINDING`
- Disposition: `PARTLY ACCEPTED / CLAIM REFINED`
- Evidence & Resolution:
  - GitHub Issues prove that C02, C03, and C05 work was tracked: [#6](https://github.com/Zephy-X/AutaRepo/issues/6), [#8](https://github.com/Zephy-X/AutaRepo/issues/8), [#12](https://github.com/Zephy-X/AutaRepo/issues/12), and [#13](https://github.com/Zephy-X/AutaRepo/issues/13). Their linked PRs and commits provide additional traceability.
  - Issue tracking alone does not establish that every mandatory output was produced or independently verified.
  - Existing evidence is distributed across `docs/baseline-v0.2-change-record.md`, `docs/specification/C03/`, `docs/specification/C04/part-2.md`, `docs/architecture-and-decisions.md`, and the linked GitHub records.
  - The original finding therefore remains valid as a traceability/documentation gap, but the statement that C02/C03/C05 evidence was simply missing was too broad. C02 and C03 Part A are verified; C03 Part B and C05 remain partly verified because of naming, centralization, and independent-verification gaps.
  - Fix: added the evidence index above and the equivalent Czech index, with exact paths, URLs, commits, statuses, and remaining gaps. No repository tag for these deliverables was found.

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
- F3 documentation gap: refined rather than rejected; GitHub tracking is now credited separately from repository evidence, and remaining C03 Part B/C05 gaps are explicitly recorded.
- Automated verification: `./mvnw.cmd test` exited successfully with code `0`.

---

## C08 — Before baseline and test evidence

Change Card, impact and options: [`intent-and-change.md#c08--controlled-change`](intent-and-change.md#c08--controlled-change).

### Step 0 — Frozen before baseline

| Item | Recorded value |
|---|---|
| Commit | `c8db481f5913753e2e86abb5791ebbb875e78963` (`main`). Production code under `src/main` is **unchanged** from this commit |
| Added for C08 (test-only) | `src/test/java/.../feature/reservation/ReservationC08BaselineIntegrationTest.java` (commit it together with these docs so others can restore the same state) |
| Accepted spec | Baseline v0.3: BR-01, BR-02, BR-03, BR-06 in `intent-and-change.md`. OP-03, OP-04, OP-05 in `docs/specification/operations/`. Statechart `docs/specification/diagrams/state-diagram.mmd`. ADR-01 + C05 in `architecture-and-decisions.md` |
| Runtime used for execution | OpenJDK **26.0.2.1** (the pom targets `java.version` **21**; run with JDK 21 to reproduce the declared runtime exactly), Maven 3.9.16 via `./mvnw`, Spring Boot **4.1.1** (parent POM), Windows 11 |
| Test databases | (a) H2 2.4.240 in-memory `c08baseline`, PostgreSQL mode, `create-drop`. (b) PostgreSQL **16.15** (`postgres:16` from `docker-compose.yml`), dedicated disposable database `autorepo_c08`, `create-drop`. The dev database `autorepodb` was **not** touched |
| Schema version | No versioned migrations. Schema generated by Hibernate from the entities at `c8db481`. `reservations` = `id, approved_at, created_at, end_time, review_deadline, start_time, state (CHECK in 6 enum values), submitted_at, approver_id→users, user_id→users, vehicle_id→vehicles` + indexes `idx_reservation_range(vehicle_id,start_time,end_time)`, `idx_reservation_vehicle_state(vehicle_id,state)`. Timestamps are `timestamp(6) without time zone` |
| Clock | Test overrides the `Clock` bean with `Clock.fixed(2026-12-01T06:00:00Z, UTC)` (`@Primary`). Scheduler interval raised to 1 h so OP-07 cannot sweep mid-test |
| Isolation / data setup | No class-level `@Transactional`. Every service call commits its own transaction. `@BeforeEach` deletes all rows and inserts user `C08 Tester` + standard vehicle **R1** (`requiresApproval=false`, active). Oracles (expected results) read committed rows with `ReservationRepository.findById/findByState` in fresh transactions |
| Commands (no passwords) | See [C08 — Reproducible test commands](#c08--reproducible-test-commands) below |

### Pre-existing suite at the baseline (executed 2026-11-15)

- `./mvnw -B test` with Docker **not** running → `Tests run: 21, Failures: 0, Errors: 1`. The error was `ReservationPersistenceSpikeTest` (*Failed to load ApplicationContext*, because it needs PostgreSQL at `localhost:5432`). This is an environment precondition, not a code defect.
- `./mvnw -B test` with the compose `postgres` service up, including the new C08 class → `Tests run: 25, Failures: 0, Errors: 0, Skipped: 1` (the skip is TC-C08-04, see below). **BUILD SUCCESS**. Log: [`evidence/c08/run4-full-suite-c8db481.txt`](evidence/c08/run4-full-suite-c8db481.txt)

### C08 / TC-C08-01 — positive: adjacent confirmation keeps BR-02

- **Test basis:** BR-01 (half-open), BR-02 (no overlapping blocking reservations), OP-03 REQ-03 @ Baseline v0.3. Risk link: R-01 (only the sequential part; not a race test).
- **Baseline / command:** `c8db481`; `./mvnw -B test -Dtest=ReservationC08BaselineIntegrationTest` (H2) and the PostgreSQL variant from README.
- **Preconditions / initial persisted state:** isolated store. R1 active, standard. B1 `CONFIRMED` R1 `[2026-12-01T10:00, 11:00)`. B2 `DRAFT` R1 `[11:00, 12:00)`. Server time `06:00 UTC` (≥ 2 h before start, so the confirm preconditions hold). Role precondition: **N/A (BLOCKED at baseline)**, because no role model exists (see Known limitations).
- **Test data / timezone:** all times are UTC `LocalDateTime`.

| # | Action / input | Expected BEFORE execution | Actual AFTER execution | Evidence |
|---|---|---|---|---|
| 1 | Reset store, insert R1, B1, B2 | One `CONFIRMED`, one `DRAFT` | As expected | `@BeforeEach` + `persist(...)` |
| 2 | Independently read B1, B2 and the count of `CONFIRMED` | B1 `CONFIRMED`, B2 `DRAFT`, 1 confirmed | B1 `CONFIRMED`, B2 `DRAFT`, 1 | assertions lines in `tcC0801` |
| 3 | `ReservationService.confirmReservation(B2)` (real service, own tx) | Succeeds, returns `CONFIRMED` (adjacent ≠ overlap) | Returned `CONFIRMED`; "confirmed" notification logged | run logs |
| 4 | Read committed `CONFIRMED` rows, check pairwise overlap | Exactly {B1, B2}, no overlap | {B1, B2}, no overlap | `assertNoOverlappingConfirmed` |
| 5 | Re-run (reset in `@BeforeEach`) | Same outcome | Same on H2 and PostgreSQL | run1, run2 |

- **Verdict:** **PASS** (H2 run1 and PostgreSQL 16.15 run2, 2026-11-15).
- **Remaining untested:** parallel confirmation of overlapping drafts (R-01). An HTTP-level call through `POST /reservations/{id}/confirm` was not automated. The service is the authoritative owner per ADR-01.

### C08 / TC-C08-02 — negative: overlapping confirmation is rejected

- **Test basis:** BR-02, OP-03 failure `CONFLICT_OVERLAP` @ v0.3. Interface contract: `ConflictOverlapException("CONFLICT_OVERLAP")`. The UI turns this into the flash message "Vehicle conflict…" (no HTTP 409; this is an SSR app).
- **Preconditions:** B1 `CONFIRMED` R1 `[10:00, 11:00)`. B2 `DRAFT` R1 `[10:30, 11:30)`. Clock `06:00 UTC`.

| # | Action / input | Expected BEFORE execution | Actual AFTER execution | Evidence |
|---|---|---|---|---|
| 1 | Reset, insert R1, B1, B2 | Only B1 `CONFIRMED` | As expected | `@BeforeEach` |
| 2 | Read initial stored state | Overlapping times, 1 confirmed | B1 `CONFIRMED`, B2 `DRAFT` | assertions |
| 3 | `confirmReservation(B2)` | Rejected: `ConflictOverlapException`, message `CONFLICT_OVERLAP` | Thrown as expected | `assertThatThrownBy` |
| 4 | Read committed state after the tx | B1 unchanged `CONFIRMED`, B2 still `DRAFT`, 1 confirmed | As expected | assertions |
| 5 | Repeat after reset | Same | Same (H2 + PostgreSQL) | run1, run2 |

- **Verdict:** **PASS** (H2 + PostgreSQL 16.15).

### C08 / TC-C08-03 — current cancellation behaviour (the "before" of CR-08)

- **Test basis:** BR-03 v0.3 / OP-04 REQ-05: cancel iff `now < startTime`.
- **Preconditions:** Clock `06:00 UTC`. C1 `CONFIRMED` R1 `[07:00, 09:00)` (starts in 1 h). C2 `CONFIRMED` R1 `[06:00, 08:00)` (starts now).

| # | Action / input | Expected BEFORE execution | Actual AFTER execution | Evidence |
|---|---|---|---|---|
| 1 | `cancelReservation(C1)` at `start − 1h` | Allowed under v0.3 → committed `CANCELLED` | `CANCELLED`, notification logged | `tcC0803` |
| 2 | `cancelReservation(C2)` at `now == start` | `CancellationWindowClosedException("CANCELLATION_WINDOW_CLOSED")`, state stays `CONFIRMED` | As expected | `tcC0803` |

- **Verdict:** **PASS**. This documents the prior behaviour. **Under CR-08 step 1 must flip** to `CANCELLATION_CUTOFF_REACHED` (C09 updates this test).

### C08 / TC-C08-04 — probe: late approval persists `EXPIRED` (OP-05 Window Expired)

- **Test basis:** OP-05 alternative outcome *Window Expired* + BR-06 @ v0.3.
- **Preconditions:** luxury vehicle (`requiresApproval=true`). P `PENDING_APPROVAL` `[10:00, 12:00)`, `submittedAt = now − 2d`, `reviewDeadline = 05:00` (1 h ago). Clock `06:00 UTC`.

| # | Action / input | Expected BEFORE execution | Actual AFTER execution | Evidence |
|---|---|---|---|---|
| 1 | `approveReservation(P)` | Rejected with `ApprovalWindowExpiredException("APPROVAL_WINDOW_EXPIRED")` | Thrown as expected. Log `NOTIFICATION [STUB]: Reservation 1 expired` | run1, run3 |
| 2 | Read committed state of P | `EXPIRED` | **`PENDING_APPROVAL`** (`expected: EXPIRED but was: PENDING_APPROVAL`) | run1 (H2), run3 (PostgreSQL) |

- **Verdict:** **FAIL** (H2 and PostgreSQL 16.15, 2026-11-15).
- **Contradicted claim / defect:** OP-05 "triggers transition to `EXPIRED`". Root cause: rollback of the `@Transactional` method on the thrown `RuntimeException`. Recorded as **DEF-C08-01** in [`intent-and-change.md`](intent-and-change.md#c08--def-c08-01--late-approval-expired-transition-is-rolled-back-defect-found-in-step-2).
- **Current state of the test:** `@Disabled("DEF-C08-01: observed FAIL …")` so the baseline build stays green. Run it explicitly with `-Djunit.jupiter.conditions.deactivate=org.junit.*DisabledCondition`. C09 removes `@Disabled` when the fix lands.
- **Residual exposure:** the soft-lock stays until the next scheduler sweep (≤ 60 s by default). The renter gets a contradictory "expired" notification. BR-02 is not violated.

### C08 evidence files

| File | Content |
|---|---|
| [`evidence/c08/run1-baseline-c8db481.txt`](evidence/c08/run1-baseline-c8db481.txt) | H2 run, 4 cases, TC-04 FAIL (before `@Disabled`) |
| [`evidence/c08/run2-postgres16-c8db481.txt`](evidence/c08/run2-postgres16-c8db481.txt) | PostgreSQL 16.15 run: 3 PASS, 1 skipped |
| [`evidence/c08/run3-postgres16-tc04-enabled-c8db481.txt`](evidence/c08/run3-postgres16-tc04-enabled-c8db481.txt) | PostgreSQL run with TC-04 force-enabled: FAIL `expected: EXPIRED but was: PENDING_APPROVAL` |
| [`evidence/c08/run4-full-suite-c8db481.txt`](evidence/c08/run4-full-suite-c8db481.txt) | Full suite: 25 run, 0 failures, 1 skipped |

### C08 — Reproducible test commands

**1. C08 test cases on isolated in-memory H2 (no Docker needed):**

```bash
./mvnw -B test -Dtest=ReservationC08BaselineIntegrationTest
```

**2. The same cases on real PostgreSQL 16, using a dedicated disposable database** (the test deletes every row, so never point it at `autorepodb`):

```bash
docker compose up -d postgres
docker exec local_postgres psql -U postgres -c "CREATE DATABASE autorepo_c08;"
# credentials = the docker-compose defaults (see README); set C08_DB_PASSWORD yourself
export C08_DB_URL=jdbc:postgresql://localhost:5432/autorepo_c08
export C08_DB_DRIVER=org.postgresql.Driver
export C08_DB_USER=postgres
export C08_DB_PASSWORD=<compose password>
export C08_DB_DIALECT=org.hibernate.dialect.PostgreSQLDialect
./mvnw -B test -Dtest=ReservationC08BaselineIntegrationTest
```

**3. Re-run the disabled defect probe TC-C08-04 (DEF-C08-01, expected to FAIL until C09):**

```bash
./mvnw -B test -Dtest=ReservationC08BaselineIntegrationTest "-Djunit.jupiter.conditions.deactivate=org.junit.*DisabledCondition"
```

**4. Full suite** (`ReservationPersistenceSpikeTest` needs the compose `postgres` service running, otherwise it errors with *Failed to load ApplicationContext*):

```bash
./mvnw -B test
```

On Windows PowerShell use `./mvnw.cmd` and `$env:C08_DB_URL="..."` instead of `export`.

### C08 — Definition of Done

- [x] Exact before baseline and its accepted behaviour identified (`c8db481`, Baseline v0.3)
- [x] Two stepwise test cases with data and expectations written before execution (TC-C08-01/02, plus TC-C08-03/04)
- [x] At least one actually executed through real persistence: all four run on H2 **and** PostgreSQL 16.15 (TC-04 FAIL → DEF-C08-01)
- [x] One credible CR (CR-08 cancellation cut-off) with a full Impact Matrix (justified NO IMPACT) and an Options Matrix
- [x] Decision recorded: ACCEPT Option A, owner Radim Pukalík, 2026-11-15
- [ ] **Peer review:** Adam Murcko writes his review in the Change Card (`intent-and-change.md` → Peer review → `TODO`)
- [x] Concrete C09 hand-off (bounded delta, new oracles (expected results), regression set)
- [x] Links live in the existing repository; known limitations recorded

### C08 status summary

- TC-C08-01 **PASS**, TC-C08-02 **PASS**, TC-C08-03 **PASS** (prior behaviour), TC-C08-04 **FAIL → DEF-C08-01**.
- CR-08: **ACCEPTED, Option A** (owner Radim Pukalík, 2026-11-15; reviewer Adam Murcko, review pending). PLANNED for C09: not implemented, not verified.
- DEF-C08-01: **ACCEPTED** as a C09 defect fix (owner Radim Pukalík).
- Not run: parallel-confirmation race test (R-01), HTTP-level UI flows, role-precondition steps (BLOCKED: no role model at the baseline).
