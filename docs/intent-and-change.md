# Project Frame (Baseline v0.3)

## Reservation domain

Shared vehicle / car reservation fleet with tiered luxury/restricted approval governance, exclusive soft-locking, and automated background expiration.

## Purpose

A web-based reservation platform for individuals and organizations to check availability and reserve vehicles. It eliminates scheduling conflicts, guarantees exclusive availability for scheduled pickup windows, prevents double-booking via pre-emptive soft-locks, and routes restricted/luxury vehicle requests through an authorized approval gate before final allocation commitment.

## Users / Stakeholders

- **User / Renter**: Browses inventory, drafts bookings, submits reservations for immediate confirmation or approval review, and cancels eligible reservations prior to departure.
- **Fleet Manager / Approver**: Inherits all standard User capabilities; oversees operational vehicle statuses (`isActive`) and reviews, approves, or rejects pending requests for high-tier/luxury resources (`requiresApproval`).
- **System (Scheduler / Background Worker)**: Automated background actor responsible for enforcing review SLAs and triggering timeouts (`OP-07`) for unreviewed requests.

## Core concepts

- **Resource (Vehicle)**: The car being reserved (`id`, `brand`, `model`, `licensePlate`, `isActive`, `requiresApproval` / luxury classification).
- **Reservation**: The reservation record with a half-open time range `[startTime, endTime)`, links to Resource and User, and an operational lifecycle status.
- **User**: The client creating and interacting with reservations (includes role and approver privilege markers).
- **ReservationState**: Lifecycle enum (`DRAFT`, `PENDING_APPROVAL`, `CONFIRMED`, `REJECTED`, `CANCELLED`, `EXPIRED`).
- **Soft-Lock**: An exclusive provisional hold established during `PENDING_APPROVAL` that blocks `OP-02 Availability` and prevents any concurrent user from taking the slot while review is underway.
- **Hard-Lock**: A permanent, committed allocation (`CONFIRMED`) that guarantees the vehicle for the scheduled interval.

## Core operations (Baseline v0.3)

- **OP-01 Create Reservation**: User records booking intent by creating a new reservation in `DRAFT` state for a chosen vehicle. No allocation or inventory lock is committed.
- **OP-02 Check Availability**: Read-only query evaluating whether an exclusive vehicle is available for a requested half-open interval `[start, end)`. Any overlapping interval in `CONFIRMED` or `PENDING_APPROVAL` is treated as unavailable.
- **OP-03 Confirm Reservation**: Evaluates conflict across `CONFIRMED` and `PENDING_APPROVAL` records. Converts eligible standard `DRAFT` reservations directly into a committed, binding allocation (`CONFIRMED`). For vehicles requiring approval (`requiresApproval == true`), places an exclusive **soft-lock** on the slot and transitions the record to `PENDING_APPROVAL`.
- **OP-04 Cancel Reservation**: Withdraws a `DRAFT`, `PENDING_APPROVAL`, or `CONFIRMED` reservation prior to its start time (`currentTime < startTime`), transitioning it to `CANCELLED` and releasing any soft- or hard-locks immediately back to the availability pool.
- **OP-05 Approve Reservation**: An authorized Approver reviews a `PENDING_APPROVAL` reservation and converts the existing soft-lock into a binding allocation (`CONFIRMED`), exclusively locking the vehicle under `BR-02`.
- **OP-06 Reject Reservation**: An authorized Approver declines a `PENDING_APPROVAL` request, transitioning it to `REJECTED` and dissolving the soft-lock immediately.
- **OP-07 Expire Pending Reservation**: Automated system operation executed by the scheduler when an unreviewed request reaches its deadline (`currentTime >= reviewDeadline` or `currentTime >= startTime`), transitioning the state to `EXPIRED` and releasing the soft-lock.
- **Manage Fleet (Resource Admin)**: Maintenance operations to mark vehicles as active/inactive (`isActive = true/false`) and configure authorization requirements (`requiresApproval = true/false`).

## Persistent state

- **Vehicle**: `id` (PK), `brand`, `model`, `vin`, `licensePlate`, `isActive` (boolean), `requiresApproval` (boolean flag denoting approval gate requirement).
- **User**: `id` (PK), `fullName`, `email`, `role` (enum / identifier for approver privileges).
- **Reservation**: `id` (PK), `vehicle_id` (FK), `user_id` (FK), `startTime` (timestamp), `endTime` (timestamp), `state` (enum: `DRAFT`, `PENDING_APPROVAL`, `CONFIRMED`, `REJECTED`, `CANCELLED`, `EXPIRED`), `reviewDeadline` (timestamp, set upon submission to `PENDING_APPROVAL`), `approver_id` (FK, nullable), `approvedAt` (timestamp, nullable), `submittedAt` (timestamp, nullable), `createdAt` (timestamp).

## State-changing operations

- `[none]` → `DRAFT` (`OP-01` Create Reservation)
- `DRAFT` → `CONFIRMED` (`OP-03` Confirm Reservation, standard resources only; enforces conflict check under `BR-02`)
- `DRAFT` → `PENDING_APPROVAL` (`OP-03` Confirm Reservation, triggered when `vehicle.requiresApproval == true`; acquires exclusive soft-lock)
- `PENDING_APPROVAL` → `CONFIRMED` (`OP-05` Approve Reservation, executed by Approver; converts soft-lock to hard allocation)
- `PENDING_APPROVAL` → `REJECTED` (`OP-06` Reject Reservation, executed by Approver; releases soft-lock)
- `PENDING_APPROVAL` → `EXPIRED` (`OP-07` Expire Pending Reservation, automated system transition when `currentTime >= reviewDeadline` or `currentTime >= startTime`; releases soft-lock)
- `DRAFT` → `CANCELLED` (`OP-04` Cancel Reservation, user cancellation)
- `PENDING_APPROVAL` → `CANCELLED` (`OP-04` Cancel Reservation, user cancellation prior to start time; releases soft-lock)
- `CONFIRMED` → `CANCELLED` (`OP-04` Cancel Reservation, releases committed vehicle allocation)

## Common business rules & invariants

- **BR-01 (Interval Semantics)**: All reservation periods use half-open interval semantics `[startTime, endTime)`. An adjacent booking starting at `endTime` does not collide. Intervals require strictly `startTime < endTime`.
- **BR-02 (Exclusive Resource Invariant & Blocking States)**: At no committed system state may two blocking reservations overlap for the exact same vehicle:

  $$
  \max(A.start, B.start) < \min(A.end, B.end)
  $$

  Both `CONFIRMED` (hard-lock) and `PENDING_APPROVAL` (soft-lock) act as blocking states. Non-blocking states are `DRAFT`, `CANCELLED`, `REJECTED`, and `EXPIRED`.
- **BR-03 (Cancellation Policy)**: A reservation in `DRAFT`, `PENDING_APPROVAL`, or `CONFIRMED` may be cancelled if and only if `currentTime < Reservation.startTime`. Transition to `CANCELLED` immediately frees all inventory locks. Terminal states cannot be re-cancelled.
- **BR-04 (Draft Non-Allocation)**: A `DRAFT` reservation does not claim or block vehicle availability in `OP-02`. Overlapping drafts compete on a first-to-confirm basis at `OP-03`.
- **BR-05 (Luxury Vehicle Policy & Authorization Lifecycle)**: A vehicle with `requiresApproval == true` cannot transition directly from `DRAFT` to `CONFIRMED`. At `OP-03`, it transitions to `PENDING_APPROVAL`, acquiring an exclusive soft-lock until an Approver explicitly executes `OP-05` (Approve) or `OP-06` (Reject).
- **BR-06 (Time-to-Live & Automatic Expiration)**: Every reservation in `PENDING_APPROVAL` has a review window defined by $\text{reviewDeadline} = \min(\text{submittedAt} + \Delta T\_{\text{SLA}}, \text{Reservation.startTime})$. If `currentTime >= reviewDeadline` or `currentTime >= startTime` without a manager decision, `OP-07` transitions the record to `EXPIRED` and frees the soft-lock.
- **BR-07 (Resource Exclusivity Assumption)**: Every vehicle is a discrete physical entity with capacity = 1. Pooled and fractional allocations are out of scope.

## Domain-specific business rule

- **BR-Domain (Lead Time & Max Duration)**: A reservation cannot be created in the past (`startTime > currentTime`), and the reservation duration must strictly satisfy `0 < (endTime - startTime) <= 14 days`.

## External / system boundary

- **Notification Service**: External messaging boundary notified when a reservation transitions to `PENDING_APPROVAL`, `CONFIRMED`, `REJECTED`, `CANCELLED`, or `EXPIRED`.
- **System Clock / Scheduler**: External time authority and worker daemon driving temporal invariants and executing `OP-07` automated expiration sweeps.

## Assumptions

- Approvers inherit all permissions of standard Users via actor generalization.
- Background worker execution for `OP-07` runs on fixed cron intervals supplemented by lazy evaluation checks during access.
- Standard UTC timestamps are used for all temporal comparisons.

## Resolved architectural unknowns (from v0.2)

- **Pending Hold Semantics**: Resolved. `PENDING_APPROVAL` places an exclusive **soft-lock** on the resource interval. It blocks `OP-02 Availability` and prevents competing reservations from claiming the slot while manager review is in flight.
- **Stale Reservation Expiration**: Resolved. Bounded by an automated review SLA deadline via `OP-07` to prevent cars from hanging in soft-locked limbo.

## Selected future pressure

- **Category**: Q (Quality / Concurrency, Contention & Temporal Consistency)
- **Concrete pressure**: High-concurrency race conditions during `OP-03` confirmation submissions when multiple users attempt to confirm overlapping `DRAFT` reservations for the same vehicle simultaneously.
- **Relevance to our system**: While soft-locking eliminates collisions during approval (`OP-05`), the initial confirmation step (`OP-03`) must employ database-level isolation (e.g., serializable transactions, `SELECT ... FOR UPDATE` row locks, or exclusion constraints on `[startTime, endTime)`) to guarantee that only one concurrent transaction successfully claims the vehicle into `CONFIRMED` or `PENDING_APPROVAL`.

---

# C08 — Controlled Change

> **Why not the course example (pending approval expiry)?** Our baseline already implements it: BR-06 / OP-07, `ReservationRules.shouldExpire`, the lazy check in `ReservationService.approveReservation` and `ReservationExpirationScheduler`. Re-specifying it would not be a change. We therefore apply the C08 procedure to a **cancellation-rule change (CR-08)**. Step 2 of the procedure also uncovered a real defect in the existing expiry path (**DEF-C08-01**). It is recorded separately below because it is a defect against the accepted spec, not a new requirement.
>
> Test cases and execution evidence: [`evidence-and-evolution.md#c08--before-baseline-and-test-evidence`](evidence-and-evolution.md#c08--before-baseline-and-test-evidence). Reproducible commands: [`evidence-and-evolution.md#c08--reproducible-test-commands`](evidence-and-evolution.md#c08--reproducible-test-commands).

## C08 / CR-08 — Controlled Change Card: cancellation cut-off for confirmed reservations

**Status:** `ACCEPTED — Option A` (owner Radim Pukalík, 2026-11-15). Peer review by Adam Murcko is **pending** (see Peer review). CR-08 is **PLANNED for C09**: not implemented, not verified (PLANNED ≠ IMPLEMENTED ≠ VERIFIED).

### Change source and concrete project need

- **Source:** Fleet Manager / Approver stakeholder (see *Users / Stakeholders* above).
- **Need:** "When a renter cancels a confirmed car shortly before pickup, the car has already been prepared (cleaned, fuelled, keys handed to the desk) and we have no time to offer it to anyone else. Confirmed bookings must not be withdrawable at the last minute."
- **Objective:** guarantee the fleet a minimum re-allocation window before a committed (hard-locked) pickup.

### Before baseline

| Item | Value |
|---|---|
| Commit | `c8db481f5913753e2e86abb5791ebbb875e78963` (`main`, 2026-11-08) |
| Accepted spec | Baseline v0.3 (this file): **BR-03** Cancellation Policy, **OP-04** [`OP-04-cancel-reservation.md`](specification/operations/04-cancel-reservation/OP-04-cancel-reservation.md) (REQ-05), statechart [`state-diagram.mmd`](specification/diagrams/state-diagram.mmd), detailed rule text [`business-rules.md#br-03--cancellation-policy`](business-rules.md#br-03--cancellation-policy) |
| Architecture decision | ADR-01 + C05 detailed design in [`architecture-and-decisions.md`](architecture-and-decisions.md): `ReservationRules` owns reusable rules, `ReservationService` owns transitions |
| Schema | No versioned migrations; schema is generated by Hibernate (`ddl-auto: update` dev / `create-drop` test) from the JPA entities at `c8db481`. `reservations` columns are recorded in the evidence file |
| Config | `MIN_LEAD_TIME = 2h`, `MAX_DURATION = 14d`, `REVIEW_SLA = 24h` hard-coded in `ReservationRules`; `Clock.systemUTC()` bean (`TimeConfig`); scheduler `autorepo.expiry-interval` (default 60 000 ms) |

### Previous observable behaviour / limitation

BR-03 v0.3 / REQ-05: `DRAFT`, `PENDING_APPROVAL` and `CONFIRMED` may be cancelled **iff `currentTime < startTime`**. A `CONFIRMED` booking can therefore be cancelled 1 minute before pickup. This was executed and observed in **TC-C08-03** (cancel at `start − 1h` → `CANCELLED`).

### New intended behaviour, scope and explicit out-of-scope

- **New rule (proposed BR-03 v0.4):** a reservation in `CONFIRMED` may be cancelled **iff `currentTime < startTime − CANCELLATION_CUTOFF`**, where `CANCELLATION_CUTOFF = 2 h` (same value as the existing `MIN_LEAD_TIME`, so a renter cannot use cancel + re-create to get round it). A late attempt is rejected and the state stays `CONFIRMED` (the hard-lock is kept).
- **Unchanged:** `DRAFT` and `PENDING_APPROVAL` keep the v0.3 rule `currentTime < startTime`. A draft holds no allocation, and a pending soft-lock that is released late frees a car nobody had committed to yet.
- **Time semantics:** server time from the injected `Clock` (UTC). `startTime` is stored as `timestamp without time zone` and interpreted as UTC (existing assumption, see R-05).
- **Out of scope (not decided by CR-08):** cancellation fees or penalties, a new `LATE_CANCELLED` state, an approver override (no role model exists, see Known limitations), a configurable or per-vehicle cut-off, a scheduler or worker, UI redesign.

### Acceptance conditions

All conditions assume a fixed server clock, exclusive vehicle R1, and a `CONFIRMED` reservation `B` with `startTime = S`.

| ID | Type | Condition (observable contract) |
|---|---|---|
| AC-01 | positive | At `now = S − 2h − 1min`, Cancel(B) succeeds: committed state `CANCELLED`, and OP-02 reports the interval `AVAILABLE`. |
| AC-02 | negative | At `now = S − 1h`, Cancel(B) is rejected with a **distinct** error code `CANCELLATION_CUTOFF_REACHED`. The user sees "Confirmed reservations cannot be cancelled less than 2 hours before start." Committed state stays `CONFIRMED` and the interval stays `UNAVAILABLE`. |
| AC-03 | boundary | At **exactly** `now = S − 2h`, Cancel(B) is **rejected** (same as AC-02). The allowed window is half-open, `now < S − 2h`, consistent with BR-01. |
| AC-04 | boundary | At `now = S`, Cancel(B) is still rejected with `CANCELLATION_WINDOW_CLOSED` (unchanged code for "already started"). |
| AC-05 | regression | `DRAFT` and `PENDING_APPROVAL` reservations at `now = S − 1h` can still be cancelled. At `now = S` they are rejected with `CANCELLATION_WINDOW_CLOSED`. |
| AC-06 | regression | BR-02 stays enforced: TC-C08-01 (adjacent confirm allowed) and TC-C08-02 (overlapping confirm rejected) give the same verdicts. |
| AC-07 | regression | Terminal states are unchanged: re-cancel of `CANCELLED` → `ALREADY_CANCELLED`. `REJECTED`/`EXPIRED` → `INVALID_STATE_TRANSITION`. |

The requirement is "a confirmed booking cannot be withdrawn within 2 h of pickup". *How* (a guard in `ReservationRules`, a property, a DB trigger…) is a design option and is compared below.

### Impact analysis method and exact inspected artifacts

Method: start from AC-02 → locate the decision owner (ADR-01/C05: `ReservationRules.canCancel` is called by `ReservationService.cancelReservation` **and** by `Reservation.cancel`/`Reservation.isCancellable`) → trace callers to controller, templates, persistence and tests → look for other real writers of `state` → check configuration, release and operations.
Inspected at `c8db481`: `ReservationRules.java`, `Reservation.java`, `ReservationService.java`, `ReservationViewController.java`, `DomainErrorMessages.java`, `GlobalExceptionHandler.java`, `ReservationExpirationScheduler.java`, `DataInitializer.java`, `templates/feature/reservation/list.html` + `detail.html`, all 5 test classes, `application*.yml`, `docker-compose.yml`, `.github/`, BR-03 / OP-04 / statechart / C05 docs.

| Dimension / exact location | CHANGE / CHECK / NO IMPACT and why | Action / check | Owner / completion evidence |
|---|---|---|---|
| Accepted spec: BR-03 (`intent-and-change.md`, `business-rules.md` + `-cz`), OP-04 / REQ-05 + flow `.mmd`, `operations-summary-table.md` | **CHANGE**: the rule itself changes for `CONFIRMED` | Write BR-03 v0.4 / REQ-05 v0.4 with AC-01..AC-07 and the new error outcome | Spec owner; reviewed spec diff in the C09 PR |
| State model `state-diagram.mmd` | **CHANGE**: guard on `CONFIRMED → CANCELLED` becomes `[currentTime < start − 2h]`, other cancel guards unchanged. No new state | Update the guard label | Spec owner; diagram diff |
| Architecture / owner (ADR-01, C05) | **CHECK**: the owner stays `ReservationRules` (rule) + `ReservationService` (transition). No new component, no ADR change. C05 contract table row `cancelReservation(id)` needs the new failure code | Update the C05 contract row only | Architect; doc diff |
| Detailed design: `ReservationRules.canCancel` (static, also used by the entity) | **CHANGE**: add a `CONFIRMED_CANCELLATION_CUTOFF` constant and a state-specific window | Unit contract: `canCancel(CONFIRMED, S−2h, S) == false` | Developer; `ReservationRulesTest` boundary cases |
| Detailed design: `Reservation.cancel(now)` + `ReservationService.cancelReservation` (the guard is **duplicated** in both) | **CHANGE**: both must produce `CANCELLATION_CUTOFF_REACHED`, otherwise the entity and the service diverge | Map the failure to a new `CancellationCutoffReachedException` (or reuse the existing class with the new code; decide in C09) | Developer; `ReservationServiceTest` |
| Error contract: `DomainErrorMessages`, `ReservationViewController.handleAction`, `GlobalExceptionHandler` | **CHANGE**: new code + message. A new exception type must be added to the `handleAction` catch list, or it falls through to the generic 500 page | Add mapping and catch | Developer; integration test reads the flash `errorMessage` |
| UI: `list.html:217-219` (Cancel button `th:disabled` uses `#temporals.createNow()`), `detail.html:40` (cancel form shown for all 3 states) | **CHANGE**: the button would still be enabled inside the cut-off. **Note:** `createNow()` uses JVM local time, not the UTC `Clock` (existing R-05 drift) | Drive enablement from a model flag computed by the service with the injected `Clock` | Developer; manual UI check + screenshot at the C09 commit |
| Data / schema / existing rows | **CHECK → NO SCHEMA CHANGE**: the rule is evaluated at call time from `start_time`. Existing `CONFIRMED` rows that start within 2 h become non-cancellable when released (intended) | No migration. Release note | Release owner; release record |
| Other writers of `state` | **CHECK**: `ReservationExpirationScheduler` → only `PENDING_APPROVAL` (**NO IMPACT**). `DataInitializer` inserts seed rows, never cancels (**NO IMPACT**). **Direct SQL via pgAdmin is a real writer**. A DB `CHECK` constraint cannot express a rule relative to `now()`, so the API guard does not protect it | Declared limitation: manual SQL edits are an ops-only, audited activity | Ops owner; limitation recorded |
| Config / CI / release | **CHECK**: cut-off hard-coded like `MIN_LEAD_TIME` (option A) or a property (option B). **No CI pipeline exists** (`docker-image.yml` removed in `c8db481`), so the build is verified locally | Record the value in BR-03 and README. C09 runs a clean-checkout `./mvnw test` | Project lead; C09 release record |
| Tests / integration / risk | **CHANGE**: TC-C08-03 asserts the *old* behaviour (`S−1h` cancel OK) and **must flip** to AC-02. `ReservationRulesTest` line 63 and `ReservationServiceTest.cancelsBeforeStartAndNotifies` (start = now + 1 day) stay valid. New AC-01..AC-05 boundary tests needed | Update TC-C08-03, add new oracles (expected results) | Developer; executed run at the C09 commit |
| Other operations: OP-01/02/03/05/06/07 | **NO IMPACT** (code): they do not call `canCancel`. **CHECK** regression: TC-C08-01/02 for BR-02, existing approve/reject/expire tests | Re-run the full suite | Developer; full-suite log |
| Operations / support | **CHECK**: renters who need to cancel late have no self-service path. The fleet manager handles it out of band | Message text tells the user to contact the fleet manager | Product owner; acceptance of the limitation |

### Feasible alternatives with common criteria

| Option | How it works | Meets AC? | Benefits | Cost / failure / recovery | How to verify |
|---|---|---|---|---|---|
| **A: rule constant + guard in the existing owner** | `ReservationRules.CONFIRMED_CANCELLATION_CUTOFF = 2h`. `canCancel` becomes state-aware. Service + entity throw `CANCELLATION_CUTOFF_REACHED`. UI flag comes from the service | AC-01..07 yes | Smallest delta. Follows the existing pattern (`MIN_LEAD_TIME`). No schema/config change | Changing the value needs a redeploy. Rollback = revert one commit, no data to migrate | AC-01..05 boundary unit + integration tests with a fixed `Clock`. TC-C08-01/02 regression |
| **B: same guard, cut-off as property `autorepo.cancellation-cutoff` (Duration)** | As A, but the value is injected into `ReservationRules` | Yes | Ops can tune it without a code change | `canCancel` is **static** and called from the entity, so it must become an instance/bean dependency or take a parameter (larger refactor, touches `Reservation`). Misconfiguration risk (units, `0`, negative). Needs validation and a config smoke test | As A + startup test with a non-default value + invalid-value test |
| **C: no code change: notify the fleet manager on every cancellation** | Keep BR-03 v0.3. Fleet manager watches `notifyReservationCancelled` (already logged) and re-allocates manually | **AC-02/03 no** | Zero code risk | Does not meet the stated need (the car is still lost at short notice). Relies on a stub notification that only logs | Demonstrated manual process + stakeholder acceptance of the gap |
| *(rejected early)* D: new `LATE_CANCELLED` state + fee | Allow late cancel but record it | Partly | Business flexibility | New state, pricing, out of scope. No payment domain exists | n/a |

**Consequence of inaction:** BR-03 v0.3 stays in force. Confirmed cars can be withdrawn up to the last minute, and the fleet absorbs idle prepared vehicles. Option C is the only feasible no-code response, and it does not satisfy AC-02.

### Peer review

- **Peer reviewer:** Adam Murcko
- **Review:** `TODO`

### Decision

- **Decision:** **ACCEPT — Option A**. **Owner:** Radim Pukalík. **Date:** 2026-11-15.
- **Rationale:** A meets every AC with the smallest bounded delta, stays inside the accepted ADR-01 ownership, needs no schema or config migration, and is fully reversible by revert. B's only benefit (runtime tuning) is not a stated need and it needs a refactor of a static rule used by the entity. C does not meet AC-02.

### If ACCEPT: bounded C09 delta and test oracles (expected results)

| Hand-off item | Content |
|---|---|
| Before | `c8db481`, BR-03 v0.3 / REQ-05, executed TC-C08-01/02/03 (PASS, H2 + PostgreSQL 16.15) |
| Accepted change | CR-08: `CONFIRMED` cancel allowed only while `now < start − 2h`. Late attempt rejected with `CANCELLATION_CUTOFF_REACHED`, state unchanged |
| Bounded delta | `ReservationRules` (constant + state-aware `canCancel`), `Reservation.cancel`, `ReservationService.cancelReservation`, `DomainErrorMessages`, `ReservationViewController.handleAction` (catch), `list.html`/`detail.html` cancel enablement via service-computed flag; docs BR-03, OP-04, statechart, C05 row (EN + CZ) |
| New oracles (expected results; fixed `Clock`, `S` = start) | `S−2h−1min` → `CANCELLED` + OP-02 `AVAILABLE`; `S−2h` exactly → `CANCELLATION_CUTOFF_REACHED`, still `CONFIRMED`; `S−1h` → same; `S` → `CANCELLATION_WINDOW_CLOSED`; `DRAFT`/`PENDING_APPROVAL` at `S−1h` → `CANCELLED` |
| Regression | TC-C08-01, TC-C08-02 (BR-02), TC-C08-03 **updated** to the new expectation, full existing suite (`ReservationRulesTest`, `ReservationServiceTest`, `ReservationLifecycleIntegrationTest`, `ReservationPersistenceSpikeTest`) |
| Technical risk | UI enablement computed from a different clock than the service (R-05). Cancel racing a parallel Approve/Confirm on the same row is not verified (relates to R-01) |
| C09 work | Implementation PR, integration run on PostgreSQL, clean checkout `./mvnw test`, manual UI check, release decision |

### Open uncertainty / next decision trigger

- Adam's peer review. If he challenges the 2 h cut-off value, the constant changes (same delta, different value).
- If stakeholders later ask for per-vehicle or runtime-tunable cut-offs, revisit Option B.

---

## C08 / DEF-C08-01 — Late approval: `EXPIRED` transition is rolled back (defect found in Step 2)

- **Contradicted claim:** OP-05 *Window Expired*: "rejected with `APPROVAL_WINDOW_EXPIRED` (triggers transition to `EXPIRED`)", and BR-06.
- **Observed (TC-C08-04, executed on H2 and PostgreSQL 16.15 at `c8db481`):** `approveReservation` on a `PENDING_APPROVAL` row with `reviewDeadline` in the past throws `APPROVAL_WINDOW_EXPIRED` as specified, but the **committed** state is still `PENDING_APPROVAL`. The "expired" notification **was sent** anyway.
- **Root cause:** `ReservationService.approveReservation` is `@Transactional`. It calls `reservation.expire()`, `save()` and `flush()`, then throws `ApprovalWindowExpiredException` (a `RuntimeException`). Spring rolls back on unchecked exceptions, so the `EXPIRED` update is undone. The unit test `ReservationServiceTest.approvalExpiresAtStartTime` uses mocks and only checks the in-memory object, so it could not see this.
- **Impact:** the soft-lock stays until the next scheduler sweep (≤ `autorepo.expiry-interval`, default 60 s). The user is told "expired" while the database says pending. BR-02 is **not** violated (approval is still refused).
- **Decision:** **ACCEPT as a defect fix in C09**, tracked separately from CR-08. **Owner:** Radim Pukalík. **Reviewer:** Adam Murcko (review pending). **Date:** 2026-11-15.
- **C09 fix options:** `@Transactional(noRollbackFor = ApprovalWindowExpiredException.class)` on `approveReservation`, *or* do the expiry write in a `REQUIRES_NEW` transaction, *or* return a result instead of throwing. Move the notification to after commit in every case.
- **Oracle (expected result):** re-enable `ReservationC08BaselineIntegrationTest.tcC0804_lateApprovalPersistsExpired` (currently `@Disabled` with the observed failure). It must PASS on PostgreSQL.

## C08 — Known limitations of the before baseline (not changed by CR-08)

- **No role / authorization model:** OP-05/OP-06 require "Acting user has the Approver role" and recording `approver_id`, but `User` has no `role` field, no endpoint checks the actor, and `approver` is never set. Any role-precondition step is **BLOCKED** at this baseline. This is tracked as spec/code drift, not fixed in C08.
- **Clock drift (R-05):** the service uses the UTC `Clock`, but `DataInitializer`, `ReservationLifecycleIntegrationTest` and `list.html` (`#temporals.createNow()`) use JVM-local time.
- **BR-03 text vs. code:** BR-03 item 4 says re-cancel → `INVALID_STATE_TRANSITION`; the code returns `ALREADY_CANCELLED`.
- **Concurrency (R-01):** no parallel-confirm race test has been executed yet.
