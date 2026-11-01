# CP1 Unified Master Presentation

## Slide 1 — Executive Summary & Project Status

- Team: AutoRepo
- Baseline commit: `cd1f93c341afadab1c5176b5303bf100fb8eea7b`
- Reporting date: `2026-11-01`
- Overall status: **AMBER**
- Core conclusion: the C06 precondition defect in `OP-03 Confirm Reservation` was valid and is fixed; the remaining active risk is database-level concurrency proof under PostgreSQL contention, not the confirmed reservation invariant itself.
- Decision requested: proceed to CP2 only with explicit acceptance of the remaining concurrency and scheduler verification risk.

---

## Slide 2 — Scope & Delivery Matrix

| Capability | SPECIFIED | DESIGNED | IMPLEMENTED | VERIFIED | Evidence |
|---|---|---|---|---|---|
| Create reservation | YES | YES | YES | YES | `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/ReservationService.java` |
| Availability check | YES | YES | YES | YES | `ReservationRepository` + `ReservationService.isAvailable(...)` |
| Confirm / approval | YES | YES | YES | YES | `docs/specification/operations/03-confirm-reservation/OP-03-confirm-reservation.md` |
| Cancel | YES | YES | YES | YES | `ReservationService.cancelReservation(...)` + `docs/business-rules.md` |
| Business rules (BR-01..BR-06) | YES | YES | YES | PARTLY | `docs/business-rules.md` + `ReservationRules.java` |
| Test evidence / quality gate | YES | YES | PARTLY | PARTLY | `docs/evidence-and-evolution.md` + `ReservationServiceTest.java` |

- Verified: `RESOURCE_INACTIVE` and future-start guard are enforced and new tests pass.
- Open gap: concurrency verification under real PostgreSQL contention remains unproven.

---

## Slide 3 — Overview of C06 Candidate Findings & All 7 PARTLY Dispositions

| Finding / Partly item | Outcome | Resolution |
|---|---|---|
| F1 | **ACCEPTED & FIXED** | Missing guard checks in `confirmReservation` were reproduced, fixed, and verified. |
| F2 | **ACCEPTED & RESOLVED** | Real design drift existed: `ReservationService` duplicated rule logic and threw a generic overlap exception; the code was refactored to delegate to `ReservationRules` and throw `ConflictOverlapException`, matching H2/H1. |
| F3 | **ACCEPTED & FIXED** | Evidence gaps and missing commit tags were completed in `docs/evidence-and-evolution.md` and `docs/specification/C04/part-2.md`. |
| B1 | **PARTLY -> ACCEPTED** | Baseline traceability gap closed with commit marker and evidence records. |
| R4 | **PARTLY -> ACCEPTED via F1** | Confirm precondition mismatch was real and fixed. |
| R5 | **PARTLY -> ACCEPTED via F1 + F3** | Missing inactive-vehicle / date guard tests and evidence gap were closed. |
| A2 | **PARTLY -> REJECTED via F2** | Architecture is valid; no hard requirement for direct `ReservationRules` injection existed. |
| A3 | **PARTLY -> REJECTED via F2** | Specific exception type is not a product defect; invariant contract is correct. |
| D2 | **PARTLY -> ACCEPTED via F1 / REJECTED via F2** | Domain contract was missing a guard; direct `ReservationRules` injection is not required. |
| D4 | **PARTLY -> ACCEPTED via F1 + F3** | Guard validation and traceability gaps were fixed. |

**Evidence sources:** `docs/presentation_materials/SWI_C06_Assessment_Form_CZ_v4.md`, `docs/evidence-and-evolution.md`, `ReservationServiceTest.java`.

---

## Slide 4 — Finding F1 (ACCEPTED & FIXED) — Confirm Reservation Preconditions

- Issue: `ReservationService.confirmReservation(Long id)` did not reject:
  - inactive vehicles (`RESOURCE_INACTIVE`)
  - reservations whose start time had already been reached or passed (`START_MUST_BE_FUTURE`)
- Spec source: `docs/specification/operations/03-confirm-reservation/OP-03-confirm-reservation.md`
- Domain rule: `docs/business-rules.md` — BR-02 / exclusive resource invariant
- Resolution:
  - `ReservationService.java`: added active-vehicle and future-start precondition checks before the state transition.
  - `Reservation.java`: added aggregate-level guard checks in `confirm(...)` and `requestApproval(...)`.
- Verification:
  - New tests: `rejectsConfirmationForInactiveVehicle`, `rejectsConfirmationWhenStartHasAlreadyPassed`
  - Command: `./mvnw.cmd -q -Dtest=ReservationServiceTest test`
  - Result: exit code 0

---

## Slide 5 — Finding F2 (ACCEPTED & RESOLVED) — ReservationRules & Exception Class

- Reviewer claim: the H2/H1 architecture required `ReservationService` to delegate to `ReservationRules`, and the collision path needed to throw `ConflictOverlapException`.
- Resolution:
  - A real design drift existed in the baseline: `ReservationService` duplicated its own validation/expiry logic and threw the generic `IllegalArgumentException("CONFLICT_OVERLAP")` instead of the expected domain exception.
  - The fix aligned the implementation with the design by delegating validation, expiry, and cancellation rules to `ReservationRules` and by throwing `ConflictOverlapException` from `ensureNoBlockingConflict(...)`.
- Evidence:
  - `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/ReservationService.java`
  - `src/main/java/cz/vsb/autarepo/autorepo/common/domain/ReservationRules.java`
  - `src/test/java/cz/vsb/autarepo/autorepo/feature/reservation/ReservationServiceTest.java`
  - Automated regression: `./mvnw.cmd test` -> exit code `0`

---

## Slide 6 — Finding F3 (ACCEPTED & FIXED) — Commit Traceability & Evidence Gaps

- Issue: the assessment lacked explicit C02/C03 evidence coverage and commit/tag traceability.
- Resolution:
  - Added baseline and reporting identifiers in `docs/evidence-and-evolution.md`
  - Replaced placeholder commit/tag strings in `docs/specification/C04/part-2.md`
  - Confirmed baseline commit: `cd1f93c341afadab1c5176b5303bf100fb8eea7b`
- Evidence:
  - `docs/evidence-and-evolution.md`
  - `docs/specification/C04/part-2.md`
  - `docs/presentation_materials/SWI_C06_Assessment_Form_CZ_v4.md`

---

## Slide 7 — Scope & Business Invariant (OP-03 & BR-02)

- Operation: `OP-03 Confirm Reservation`
- Invariant: `BR-02` — exclusive resource guarantee
- Formula: `max(A.start, B.start) < min(A.end, B.end)` must never hold true for blocking states on the same vehicle.
- Required behavior:
  - resource is active
  - reservation is in `DRAFT`
  - current time is before `reservation.startTime`
  - no overlapping blocking reservation exists
- Spec sources:
  - `docs/specification/operations/03-confirm-reservation/OP-03-confirm-reservation.md`
  - `docs/business-rules.md`

---

## Slide 8 — Architecture & Boundary Ownership (ADR-03)

### AS-IS
- `ReservationViewController` calls into `ReservationService`.
- `ReservationService` owns confirmation / approval / rejection / expiration decisions.
- `ReservationRepository` checks overlaps and persists state.
- `VehicleService.findByIdForUpdate(...)` provides the contention boundary.

### TO-BE
- `ReservationService` remains the state-transition owner.
- `Reservation` remains the aggregate where local invariants are enforced.
- Notification boundary remains externalized.
- Scheduler continues to delegate expiration logic to the same lifecycle path.

**Relevant ADR:** `ADR-03 — Ownership of confirmation/lifecycle transition decisions under concurrency` in `docs/specification/C04/part-2.md`.

---

## Slide 9 — Detailed Design & Code Execution Path

Method trace for `ReservationService.confirmReservation(id)`:

1. Load reservation by ID.
2. Require current state is `DRAFT`.
3. Lock the vehicle via `vehicleService.findByIdForUpdate(...)`.
4. Validate `vehicle.isActive()` and `now.isBefore(startTime)`.
5. Check for a blocking overlap using `ensureNoBlockingConflict(...)`.
6. If approval is required, call `reservation.requestApproval(now)`; else `reservation.confirm(now)`.
7. Save and flush state.
8. Notify external boundary.

Failure handling:
- `RESOURCE_INACTIVE`
- `START_MUST_BE_FUTURE`
- `CONFLICT_OVERLAP`
- `INVALID_STATE_TRANSITION`

Code evidence:
- `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/ReservationService.java`
- `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/Reservation.java`

---

## Slide 10 — Executable Verification & Oracles

### Positive boundary test
- Test: `routesApprovalRequiredVehicleToPendingApproval`
- Command: `./mvnw.cmd -q -Dtest=ReservationServiceTest#routesApprovalRequiredVehicleToPendingApproval test`
- Oracle: sets state to `PENDING_APPROVAL`, issues the approval notification, and keeps it out of `CONFIRMED`.
- Result: pass

### Negative boundary test
- Test: `rejectsConfirmationForInactiveVehicle`
- Command: `./mvnw.cmd -q -Dtest=ReservationServiceTest#rejectsConfirmationForInactiveVehicle test`
- Oracle: throws `RESOURCE_INACTIVE`, leaves state in `DRAFT`, and triggers no notification.
- Result: pass

### Full targeted suite
- Command: `./mvnw.cmd -q -Dtest=ReservationServiceTest test`
- Result: exit code `0` and the suite passes.

---

## Slide 11 — Risk Matrix & Prioritized Risks (R-01 to R-05)

| Risk ID | Cause -> Event -> Consequence | Initial L×I | Strategy | Actual Residual |
|---|---|---:|---|---|
| `R-01` | Concurrent confirm attempts on the same vehicle -> same-vehicle double booking -> violation of BR-02 | `2×3 = 6` | MITIGATE | `NOT VERIFIED` |
| `R-02` | Scheduler or clock drift -> approval expiry fires too early or too late -> inventory lock unpredictability | `2×2 = 4` | MITIGATE | `NOT VERIFIED` |
| `R-03` | Single-machine setup dependence -> rebuild or demo fails on clean environment -> release-delay | `2×3 = 6` | MITIGATE | `NOT VERIFIED` |
| `R-04` | Scope creep beyond CP1 boundary -> delayed fix verification -> schedule impact | `1×3 = 3` | ACCEPT | `NOT VERIFIED` |
| `R-05` | Timezone / clock mismatch between host, JVM, and PostgreSQL -> timestamp conversion offset -> false `START_MUST_BE_FUTURE` or early approval expiration | `2×3 = 6` (HIGH) | MITIGATE | Expected: `1×1 = 1` (LOW); Actual: `NOT VERIFIED` |

**Source:** `docs/evidence-and-evolution.md`

---

## Slide 12 — Top Risk Deep Dive (R-01 & R-02)

### R-01 — Concurrency contention under PostgreSQL
- Risk statement: two users may confirm the same vehicle at the same time, violating the exclusive resource invariant.
- Treatment: run a transaction-level locking and same-vehicle parallel-confirm test before CP2.
- Owner: AutoRepo team lead
- Due: `2026-11-15`
- Status: `NOT VERIFIED`
- Trigger: duplicated confirm requests for the same `Vehicle` in the same time window.
- Contingency: stricter locking or serializable guard.

### R-02 — Scheduler / expiration SLA drift
- Risk statement: approval expiration may fire at the wrong boundary, leaving stale pending reservations or releasing them incorrectly.
- Treatment: verify clock-driven scheduler behavior and deadline boundary tests before CP2.
- Owner: Reservation domain owner
- Due: `2026-11-15`
- Status: `NOT VERIFIED`
- Trigger: pending reservations remain blocked or expire unpredictably.
- Contingency: manual expiration sweep + alerting.

### R-05 — Timezone and clock disparity (critical treatment)
- Risk statement: timezone/clock mismatch between host, JVM runtime, and PostgreSQL can shift timestamp comparisons, causing false past-start rejection or premature approval expiration.
- Treatment: enforce UTC consistently (`-Duser.timezone=UTC` / `TimeZone.setDefault("UTC")` and JDBC timezone pinning), and inject a deterministic `Clock` bean across services and scheduler paths.
- Owner: Reservation domain owner
- Due: `2026-11-15`
- Status: `NOT VERIFIED`
- Trigger: any unexpected `START_MUST_BE_FUTURE` rejection on future reservations or non-deterministic expiration behavior under timezone variance.

---

## Slide 13 — 4-Item Priority Action Plan

| Priority | Action | Owner | Due date | DoD |
|---|---|---|---|---|
| 1 | Run concurrent PostgreSQL confirmation tests | Project lead | `2026-11-15` | same-vehicle parallel confirms are blocked; zero double-booking in logs |
| 2 | Verify scheduler expiry logic | Reservation domain owner | `2026-11-15` | `APPROVAL_WINDOW_EXPIRED` occurs at the correct boundary and stale reservations release the lock |
| 3 | Validate clean-environment reproducibility | Project lead | `2026-11-12` | build/test works on a second machine using repo instructions |
| 4 | Final evidence and release pack | Product owner + project lead | `2026-11-22` | docs, brief, and status artifacts are complete and internally consistent |

---

## Slide 14 — Final Outlook & CP2 Gate Decision

- Verified complete:
  - F1 defect fixed and tested
  - F3 evidence gap closed
  - F2 rejected with architecture evidence
  - reservation lifecycle unit tests pass
- Open work packages:
  - PostgreSQL concurrency proof under same-vehicle contention
  - scheduler expiry verification under runtime conditions
  - clean-environment validation
- Main constraint: no end-to-end concurrency proof yet; this remains the primary CP2 gating risk.
- Decision requested: proceed to CP2 with explicit risk acceptance for the remaining verification work, or block release until the parallel-confirm and expiry checks are completed.

---

## Closing note

This unified deck aligns the technical defense, the C06 findings audit, and the project status into a single evidence-backed narrative anchored to baseline commit `cd1f93c341afadab1c5176b5303bf100fb8eea7b` and the validated reservation lifecycle changes in `ReservationService.java`, `Reservation.java`, and `ReservationServiceTest.java`.
