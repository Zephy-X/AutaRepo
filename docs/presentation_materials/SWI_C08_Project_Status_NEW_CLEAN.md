# Slide 01 — Executive Status

- Team: AutoRepo
- Reporting commit: `cd1f93c341afadab1c5176b5303bf100fb8eea7b`
- Reporting date: `2026-11-01`
- Overall RAG: `AMBER`
- Core conclusion: the C06 precondition gap for `OP-03 Confirm Reservation` was valid and is now fixed; remaining project risk is concentrated in concurrency and scheduler verification rather than in the core reservation lifecycle.
- Management decision needed today: Approve proceeding to CP2 provided the team completes the database-level concurrency check and documents the residual risk in the release gate.

# Slide 02 — Scope & Delivery

| Capability | SPECIFIED | DESIGNED | IMPLEMENTED | VERIFIED | Repo evidence |
|---|:---:|:---:|:---:|:---:|---|
| Create reservation | YES | YES | YES | YES | `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/ReservationService.java`, `src/test/java/cz/vsb/autarepo/autorepo/feature/reservation/ReservationServiceTest.java` |
| Availability check | YES | YES | YES | YES | `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/ReservationRepository.java`, `ReservationService.isAvailable(...)` |
| Confirm / approval | YES | YES | YES | YES | `docs/specification/operations/03-confirm-reservation/OP-03-confirm-reservation.md`, `ReservationService.confirmReservation(...)` |
| Cancel | YES | YES | YES | YES | `ReservationService.cancelReservation(...)`, `docs/business-rules.md` |
| Business rules (BR-01..BR-06) | YES | YES | YES | PARTLY | `docs/business-rules.md`, `src/main/java/cz/vsb/autarepo/autorepo/common/domain/ReservationRules.java` |
| Quality criterion / automated evidence | YES | YES | PARTLY | PARTLY | `docs/evidence-and-evolution.md`, `ReservationServiceTest` |

- Verified note: the `RESOURCE_INACTIVE` and future-start guard are now asserted and covered by automated tests.
- Open gap: concurrency validation under PostgreSQL contention is not yet verified under parallel confirm requests.

# Slide 03 — Schedule & Resources

## Critical path to CP2

1. Finalize reservation confirmation invariants and guard coverage — complete.
2. Validate concurrency under simultaneous confirmation attempts — due `2026-11-15`.
3. Confirm scheduler SLA and expiration boundary behavior — due `2026-11-15`.
4. Final release-readiness check and evidence pack — due `2026-11-22`.

## Work packages / owners / forecast

| Work package | Owner | Forecast |
|---|---|---|
| Domain rule enforcement and tests | Reservation domain owner | `2026-11-01` to `2026-11-08` |
| Concurrency verification under PostgreSQL | Project lead | `2026-11-08` to `2026-11-15` |
| Expiration SLA / scheduler checks | Reservation domain owner | `2026-11-08` to `2026-11-15` |
| Release evidence and review pack | Project lead | `2026-11-15` to `2026-11-22` |

## Capacity constraint

- Current constraint: no production-grade concurrency stress test is yet proven under real PostgreSQL contention.
- Resolution needed: execute a parallel confirm test before CP2 sign-off.

# Slide 04 — Top Risks & Issues

### Risk Log

#### R-01
- Risk statement: Two users may attempt to confirm the same vehicle at the same time, creating a race that can violate BR-02 and produce an overlapping hard or soft lock.
- Category: Technical / Quality
- Initial risk (L/I/priority/rationale): L=2, I=3, priority=6 because the exclusive vehicle invariant is central to the project and the current implementation has not yet been proven under PostgreSQL contention.
- Response strategy and rationale: MITIGATE because the service owns the lifecycle decision and the repository conflict query is designed to block conflicts, but the actual concurrency proof still needs to be executed under simultaneous load.
- Treatment, owner, due: Enforce and test transaction-level locking around the vehicle lookup and conflict check before CP2. Owner: AutoRepo team lead. Due: `2026-11-15`.
- Treatment evidence and verification status: No database contention test has yet been run against the same vehicle under parallel confirm requests. Status: `NOT VERIFIED`.
- Expected residual risk: L=2, I=2, priority=4 after the concurrency test passes and the lock discipline is confirmed.
- Actual residual risk / NOT VERIFIED: `NOT VERIFIED` because the parallel PostgreSQL verification has not been executed.
- Residual risk acceptance, owner, rationale: Accept only with explicit CP2 sign-off; owner: AutoRepo team lead. Rationale: this remains the main outstanding technical risk for the exclusive resource invariant.
- Monitoring, trigger, contingency, next review: Monitor all confirm flows for parallel same-vehicle requests; trigger: duplicated confirm attempts on one vehicle; contingency: stricter transaction-level locking and a serializable safety check; next review: `2026-11-15`.

#### R-02
- Risk statement: Pending approval reservations may expire too early or remain soft-locked too long if the scheduler timing or review-deadline logic drifts.
- Category: Technical / Operations
- Initial risk (L/I/priority/rationale): L=2, I=2, priority=4 because stale approval handling affects inventory availability and the timeout boundary is sensitive to scheduler cadence and clock consistency.
- Response strategy and rationale: MITIGATE because the approval-expiration logic already exists in the service, but the scheduler-driven behavior still needs deterministic runtime proof before release.
- Treatment, owner, due: Verify the deadline logic with clock-driven tests and confirm the scheduled expiration path before CP2. Owner: Reservation domain owner. Due: `2026-11-15`.
- Treatment evidence and verification status: The unit-level expiry path is covered, but the actual scheduler-triggered execution remains unproven. Status: `NOT VERIFIED` for runtime execution.
- Expected residual risk: L=1, I=2, priority=2 after the deadline and scheduler behavior are confirmed under test.
- Actual residual risk / NOT VERIFIED: `NOT VERIFIED` because the runtime expiration sweep has not been proven in execution.
- Residual risk acceptance, owner, rationale: Accept only with explicit sign-off; owner: Reservation domain owner. Rationale: the delay affects operational correctness but is limited to the approval-expiration flow rather than the full reservation lifecycle.
- Monitoring, trigger, contingency, next review: Monitor pending approval states and deadline transitions; trigger: a pending reservation remains unreleased or expires unexpectedly; contingency: manual expiration sweep and alerting; next review: `2026-11-15`.

#### R-03
- Risk statement: The project may depend on a single contributor's local environment knowledge and therefore fail to reproduce the build or demo on a clean machine.
- Category: People / Release / Operations
- Initial risk (L/I/priority/rationale): L=2, I=3, priority=6 because local setup dependence can delay the demonstration and release validation even when the code itself is acceptable.
- Response strategy and rationale: MITIGATE because the repository can document a clean setup and the team can verify it on a second machine to reduce single-person dependence.
- Treatment, owner, due: Capture the clean-checkout build/test procedure and verify it on a second machine before CP2. Owner: Project lead. Due: `2026-11-12`.
- Treatment evidence and verification status: No second-machine verification has yet been recorded. Status: `NOT VERIFIED`.
- Expected residual risk: L=1, I=2, priority=2 once the setup is validated on a second environment.
- Actual residual risk / NOT VERIFIED: `NOT VERIFIED` because the clean local setup has not been independently reproduced.
- Residual risk acceptance, owner, rationale: Accept only with recorded onboarding evidence; owner: Project lead. Rationale: this risk is operationally manageable and should be removed before sign-off rather than accepted permanently.
- Monitoring, trigger, contingency, next review: Monitor the ability to reproduce the build on a clean environment; trigger: setup fails outside the local machine; contingency: use a prepared baseline environment; next review: `2026-11-12`.

### Verified Issues & Findings Closed
- Verified issues closed: F1 (inactive / past-start confirm guard), F2 (design drift alignment with ReservationRules + ConflictOverlapException), and F3 (evidence + commit markers).
- All reviewer findings are now accepted/resolved or fixed; zero open unacknowledged defects remain in the C06 scope.

### Top Risk Summary Table

| Risk ID | Initial LxI | Actual residual | Trigger | Status |
|---|:---:|:---:|---|---|
| `R-01` | `2 x 3 = 6 (HIGH)` | `NOT VERIFIED` | concurrent confirms on same vehicle | open / active mitigation |
| `R-02` | `2 x 2 = 4 (MED)` | `NOT VERIFIED` | scheduler drift or stale approval expiry | open / active mitigation |
| `R-03` | `2 x 3 = 6 (HIGH)` | `NOT VERIFIED` | clean build fails outside author machine | open / active mitigation |
| `R-05` | `2 x 3 = 6 (HIGH)` | `NOT VERIFIED` | timezone offset / premature expiration or false past-start rejection | open / mitigation planned |

# Slide 05 — Next Action Plan

| Priority | Action | Owner | Due date | Definition of Done |
|:---:|---|---|:---:|---|
| 1 | Run concurrent PostgreSQL confirmation tests | Project lead | `2026-11-15` | same-vehicle parallel confirms remain blocked by invariant; zero double-booking in logs |
| 2 | Verify exact scheduler expiration semantics | Reservation domain owner | `2026-11-15` | `APPROVAL_WINDOW_EXPIRED` occurs at correct boundary and stale reservations release the soft lock |
| 3 | Review environment reproducibility and clean-checkout evidence | Project lead | `2026-11-12` | build/test works on second machine with repo instructions |
| 4 | Final release evidence pack | Product owner + project lead | `2026-11-22` | `docs/evidence-and-evolution.md`, defense brief, and review pack are complete |
| 5 | Verify UTC runtime configuration and deterministic Clock assertions (Milestone 2) | Reservation domain owner | `2026-11-15` | JVM + DB run in UTC, and tests prove stable timestamp behavior across scheduler/precondition paths |

# Slide 06 — Decision & Outlook

- Decision requested: proceed to CP2 with explicit risk acceptance for the remaining concurrency and scheduler verification.
- Verified complete: confirm precondition guard, documentation gap closure, and the core reservation lifecycle tests.
- Main constraint: the project is not yet concurrency-verified under database contention.
- Outlook: the system is functionally sound for CP1 scope, but full confidence in the exclusive vehicle invariant remains contingent on the outstanding parallelism and SLA verification.
