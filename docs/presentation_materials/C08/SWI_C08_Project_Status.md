# SWI / C08 PROJECT STATUS / EDITABLE TEMPLATE

## 01
### EXECUTIVE PROJECT STATUS
What is the current delivery position, what threatens it, and what decision is needed?

- PROJECT / TEAM: AutoRepo
- REPORTING BASELINE: `cd1f93c341afadab1c5176b5303bf100fb8eea7b`
- REPORT DATE / OWNER: `2026-11-01 / AutoRepo team`
- SWI C08 | Reporting baseline: `cd1f93c341afadab1c5176b5303bf100fb8eea7b` | Project: `AutoRepo` | Report date: `2026-11-01`

- PROJECT / TEAM
  - `AutoRepo` — reservation domain team / product delivery group
- OVERALL DELIVERY STATUS
  - `AMBER`
  - The core reservation lifecycle is implemented and validated, but the remaining CP2 gate depends on concurrency and scheduler verification under real PostgreSQL contention.
- MOST IMPORTANT DELIVERED EVIDENCE
  - `ReservationServiceTest` and the lifecycle integration tests pass; the inactive-vehicle + past-start guard fixes are implemented and validated.
  - Evidence: `src/main/java/.../ReservationService.java`, `src/main/java/.../Reservation.java`, `src/test/java/.../ReservationServiceTest.java`, `docs/evidence-and-evolution.md`.
- TOP DELIVERY CONSTRAINT
  - Parallel confirmation contention for the same vehicle and the runtime scheduler expiry path are not yet proven under actual contention; this is the main reason the project remains `AMBER` rather than `GREEN`.
- EXECUTIVE ASSESSMENT
  - The system is functionally coherent for its CP1 scope and the identified C06 defects are fixed, but the next committed milestone is only achievable with explicit verification of concurrency and expiration behavior before CP2 sign-off.
- DECISION / SUPPORT REQUIRED
  - Proceed to CP2 only with explicit risk acceptance and a defined verification plan for serializable / transactional locking and scheduler expiry behavior; owner: project lead; due: `2026-11-15`.

---

## 02
### SCOPE & DELIVERY EVIDENCE
Enter YES / PARTLY / NO (or justified N/A). A feature is VERIFIED only with relevant executed evidence.

- PROJECT / TEAM: AutoRepo
- REPORTING BASELINE: `cd1f93c341afadab1c5176b5303bf100fb8eea7b`
- REPORT DATE / OWNER: `2026-11-01 / AutoRepo team`
- SWI C08 | Reporting baseline: `cd1f93c341afadab1c5176b5303bf100fb8eea7b` | Project: `AutoRepo` | Report date: `2026-11-01`

| Capability / baseline item | Specified | Designed | Implemented | Verified | Evidence link / remaining gap |
|---|:---:|:---:|:---:|:---:|---|
| Create reservation | YES | YES | YES | YES | `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/ReservationService.java`; `ReservationServiceTest` |
| Check availability | YES | YES | YES | YES | `ReservationRepository` + `ReservationService.isAvailable(...)`; domain rules in `docs/business-rules.md` |
| Confirm / approval | YES | YES | YES | YES | `docs/specification/operations/03-confirm-reservation/OP-03-confirm-reservation.md`; `ReservationService.confirmReservation(...)` |
| Cancel reservation | YES | YES | YES | YES | `ReservationService.cancelReservation(...)`; `docs/business-rules.md` |
| Business rule / invariant | YES | YES | PARTLY | PARTLY | `docs/business-rules.md`; `ReservationRules` implemented, but concurrency proof under PostgreSQL contention is not yet executed |
| Quality scenario (C04) | YES | YES | PARTLY | PARTLY | `ReservationLifecycleIntegrationTest` and `ReservationPersistenceSpikeTest` run successfully, but no parallel confirm stress test yet |

- IMPORTANT EVIDENCE GAP
  - The project demonstrates the core lifecycle and rule enforcement, but it is not yet proven that concurrent confirm attempts are safely serialized under PostgreSQL. This is the main remaining gap between `IMPLEMENTED` and `VERIFIED` for the hard/soft lock invariant.

---

## 03
### SCHEDULE, RESOURCES & DEPENDENCIES
Use real owners and credible plan-versus-forecast dates. Report missing plans as a gap; do not invent progress.

- PROJECT / TEAM: AutoRepo
- REPORTING BASELINE: `cd1f93c341afadab1c5176b5303bf100fb8eea7b`
- REPORT DATE / OWNER: `2026-11-01 / AutoRepo team`
- SWI C08 | Reporting baseline: `cd1f93c341afadab1c5176b5303bf100fb8eea7b` | Project: `AutoRepo` | Report date: `2026-11-01`

| Work package / milestone | Owner | Committed plan | Actual / forecast | Health and evidence |
|---|---|---|---|---|
| Close C06 findings | Reservation domain owner | `2026-11-01` | `2026-11-01` | `GREEN` — F1, F2, and F3 were processed and documented in `docs/evidence-and-evolution.md` |
| Verification gap closure | Project lead | `2026-11-15` | `2026-11-15` | `AMBER` — guard fixes are verified; concurrency proof still missing |
| Integration / clean build | Project lead | `2026-11-12` | `2026-11-12` | `AMBER` — full Java test suite passes locally; second-machine reproducibility not yet recorded |
| CP2 milestone preparation | Project lead | `2026-11-15` | `2026-11-15` | `AMBER` — delivery is feasible if verification gap closes on time |

- TEAM CAPACITY / SINGLE POINT OF FAILURE
  - The reservation domain knowledge and the current repository baseline are concentrated in the same delivery group; the remaining risk is not functional design but execution proof and reproducibility.
- EXTERNAL / CRITICAL DEPENDENCY
  - PostgreSQL concurrency test execution and the scheduler expiry validation are the critical dependencies for CP2 sign-off; missing evidence here directly impacts the project release decision.

---

## 04
### TOP RISKS, TREATMENT & RESIDUAL
Use up to three risks from section B. A planned control does not lower the ACTUAL residual risk.

- PROJECT / TEAM: AutoRepo
- REPORTING BASELINE: `cd1f93c341afadab1c5176b5303bf100fb8eea7b`
- REPORT DATE / OWNER: `2026-11-01 / AutoRepo team`
- SWI C08 | Reporting baseline: `cd1f93c341afadab1c5176b5303bf100fb8eea7b` | Project: `AutoRepo` | Report date: `2026-11-01`

| Risk / source | Initial L/I + priority | Response strategy | Treatment, owner & evidence | Expected / actual residual | Trigger / acceptance |
|---|---|---|---|---|---|
| R-01 — concurrent confirmation on same vehicle | `2 / 3 / 6 (HIGH)` | `MITIGATE` | Enforce and verify transaction-level locking around vehicle lookup + conflict check before CP2; owner: project lead; evidence: no PostgreSQL parallel-confirm test yet | Expected: `2 / 2 / 4`; Actual: `NOT VERIFIED` | Trigger: duplicated confirm attempts on one vehicle; acceptance owner: project lead |
| R-02 — scheduler drift / stale pending expiry | `2 / 2 / 4 (MED)` | `MITIGATE` | Verify deadline logic and scheduler path before CP2; owner: reservation domain owner; evidence: unit tests exist, runtime path not yet proven | Expected: `1 / 2 / 2`; Actual: `NOT VERIFIED` | Trigger: reservation remains soft-locked or expires unexpectedly; acceptance owner: reservation domain owner |
| R-03 — clean build / reproducibility risk | `2 / 3 / 6 (HIGH)` | `MITIGATE` | Capture clean-checkout build/test procedure and verify on second environment before CP2; owner: project lead; evidence: no second-machine proof yet | Expected: `1 / 2 / 2`; Actual: `NOT VERIFIED` | Trigger: setup fails outside the author machine; acceptance owner: project lead |

- OPEN ISSUES (NOT RISKS)
  - No open C06 defects remain formally acknowledged; the remaining issue is proof of concurrency and expiry under actual runtime conditions, not a confirmed functional defect in the lifecycle itself.
- Risk ranking: L x I (1-2 LOW; 3-4 MEDIUM; 6-9 HIGH). Actual residual = NOT VERIFIED until treatment effectiveness is evidenced.

---

## 05
### NEXT ACTION PLAN
Prioritise four actions. Each must have one owner, a date and an observable Definition of Done.

- PROJECT / TEAM: AutoRepo
- REPORTING BASELINE: `cd1f93c341afadab1c5176b5303bf100fb8eea7b`
- REPORT DATE / OWNER: `2026-11-01 / AutoRepo team`
- SWI C08 | Reporting baseline: `cd1f93c341afadab1c5176b5303bf100fb8eea7b` | Project: `AutoRepo` | Report date: `2026-11-01`

| Priority / action | Owner | Due date | Definition of Done / evidence | Finding / risk link |
|---|---|---|---|---|
| P1 — Run concurrent PostgreSQL confirmation tests | Project lead | `2026-11-15` | Same-vehicle parallel confirms remain blocked by invariant; zero double-booking in logs | `R-01` |
| P2 — Verify exact scheduler expiration semantics | Reservation domain owner | `2026-11-15` | Expiration boundary and soft-lock release are evidenced by integration or scheduler test | `R-02` |
| P3 — Reproducible build on clean environment | Project lead | `2026-11-12` | Build/test works on second machine with repository instructions | `R-03` |
| P4 — Final CP2 evidence pack | Product owner + project lead | `2026-11-22` | `docs/evidence-and-evolution.md`, defense brief, and release review pack complete | Project gate |

- IF THE PLAN FAILS
  - Escalate to the project sponsor for a CP2 replan or scope reduction; owner: project lead; date: `2026-11-15`.

---

## 06
### MANAGEMENT CONCLUSION & DECISION
The conclusion must agree with the reporting baseline, tested evidence, risk register and action plan.

- PROJECT / TEAM: AutoRepo
- REPORTING BASELINE: `cd1f93c341afadab1c5176b5303bf100fb8eea7b`
- REPORT DATE / OWNER: `2026-11-01 / AutoRepo team`
- SWI C08 | Reporting baseline: `cd1f93c341afadab1c5176b5303bf100fb8eea7b` | Project: `AutoRepo` | Report date: `2026-11-01`

- DEMONSTRABLY COMPLETE
  - `F1` fixed and verified: inactive vehicles and past-start confirmations are rejected before transition in `ReservationService` / `Reservation`.
  - `F2` corrected and accepted: service logic delegates to `ReservationRules` and throws `ConflictOverlapException` for overlap logic, matching the design baseline.
  - `./mvnw.cmd -q test` exited successfully with code `0`; the repository’s Java test suite passed on the current baseline.
- OPEN / NOT VERIFIED
  - Parallel PostgreSQL contention test for same-vehicle confirm requests remains unexecuted.
  - Scheduler-driven expiration under runtime conditions remains not proven.
  - Clean environment reproducibility has not yet been demonstrated outside the author machine.
- TOP RISK / RESPONSE / ACTUAL RESIDUAL
  - `R-01`; owner: project lead; treatment: transaction-level locking and contention test; actual residual: `NOT VERIFIED`.
- REQUESTED DECISION / OWNER / DUE
  - Approve CP2 progression only with explicit risk acceptance and completion of the concurrency / scheduler proof; owner: project lead; due: `2026-11-15`.
- OVERALL STATUS & DELIVERY FORECAST
  - `AMBER` — the next milestone is achievable only with the remaining verification work; the core lifecycle is healthy, but the database contention proof is still the critical control gate.
