# Baseline v0.2 — Change Record & Baseline Specification (C02)

## 13. Update to Baseline v0.2

### Changed Condition
Selected high-tier resources (e.g., luxury or restricted vehicles with `requiresApproval == true`) cannot transition directly from `DRAFT` to `CONFIRMED`. They require an intermediate authorization step performed by an authorized person (Fleet Manager / Approver). Approval may be granted, rejected, or expire if unhandled prior to the reservation start time.

### Affected Requirements / Slices
- **Slice 01 (Create Reservation / OP-01):** Form and entity binding extended to track vehicle approval status (`requiresApproval`); creation persists intent as `DRAFT` without immediate locking.
- **Slice 03 (Confirm Reservation / OP-03):** Confirmation behavior branches based on resource category. If `vehicle.requiresApproval == true`, state transitions to `PENDING_APPROVAL` instead of `CONFIRMED`. Direct confirmation to `CONFIRMED` is guarded and forbidden for luxury vehicles.
- **Slice 04 (Cancel Reservation / OP-04):** Cancellation rules updated to permit cancellation of reservations in `PENDING_APPROVAL` state as long as `currentTime < startTime`.
- **Slice 05 (Approve Reservation / OP-05 - NEW):** Dedicated operation enabling an authorized Approver to review a `PENDING_APPROVAL` reservation, enforce conflict detection (`BR-02`), and transition it to `CONFIRMED`.
- **Slice 06 (Reject Reservation / OP-06 - NEW):** Dedicated operation enabling an authorized Approver to decline a `PENDING_APPROVAL` reservation, transitioning it to `REJECTED`.

### Unaffected Requirements / Slices + Reason
- **Slice 02 (Check Availability / OP-02):** Unaffected in core interval intersection mathematics. The invariant check (`max(start) < min(end)`) against existing `CONFIRMED` allocations remains structurally identical.
- **Vehicle & User Read Operations:** Listing and selection queries remain passive consumers of domain records and are unaffected by approval state transitions.

### New Actor / Operation
- **New Actor:** Approver (Fleet Manager / Authorized Person).
- **New Operations:**
    - `OP-05 Approve Reservation`: Transition `PENDING_APPROVAL` → `CONFIRMED`.
    - `OP-06 Reject Reservation`: Transition `PENDING_APPROVAL` → `REJECTED`.

### Changed Rules / State Semantics
- **BR-05 (Luxury Approval Gate):** A vehicle with `requiresApproval == true` cannot transition directly from `DRAFT` to `CONFIRMED`. Triggering confirmation moves it to `PENDING_APPROVAL`.
- **BR-06 (Approval Window & Expiration):** A reservation in `PENDING_APPROVAL` can only be approved while `currentTime < Reservation.startTime`. If the current time reaches or passes `startTime` before approval, the reservation is no longer actionable and transitions to `EXPIRED`.
- **BR-07 (Approval Conflict Check):** Approving a reservation re-validates the half-open interval `[startTime, endTime)` against all existing `CONFIRMED` reservations for that vehicle to preserve invariant `BR-02`.

### Changed Use-Case View
- Standard Users can draft reservations, check availability, request confirmation (which routes luxury vehicles to approval), and cancel eligible bookings.
- Approvers gain access to administrative review actions: listing pending requests, approving reservations (`OP-05`), and rejecting reservations (`OP-06`).

### Changed Statechart
- Added intermediate state: `PENDING_APPROVAL`.
- Added terminal states: `REJECTED` and `EXPIRED`.
- Direct path `DRAFT` → `CONFIRMED` preserved only for standard resources.
- Luxury path established: `DRAFT` → `PENDING_APPROVAL` → `CONFIRMED` | `REJECTED` | `EXPIRED` | `CANCELLED`.

### New Verification Examples
- **VE-05-POS (Successful Approval):**
    - *Setup:* Reservation `#101` in `PENDING_APPROVAL` for luxury vehicle `BMW M5`, scheduled for `[tomorrow 10:00, tomorrow 12:00)`. No overlapping confirmed bookings.
    - *Action:* Approver triggers `POST /reservations/101/approve`.
    - *Outcome:* State transitions to `CONFIRMED`; car allocation locked.
- **VE-05-NEG1 (Luxury Direct Confirmation Guard):**
    - *Setup:* Reservation `#102` in `DRAFT` for luxury vehicle `Porsche 911`.
    - *Action:* Standard user triggers `POST /reservations/102/confirm`.
    - *Outcome:* Direct transition to `CONFIRMED` is prevented; state transitions to `PENDING_APPROVAL`.
- **VE-05-NEG2 (Approval Overlap Collision):**
    - *Setup:* Reservation `#103` in `PENDING_APPROVAL` for `[tomorrow 10:00, tomorrow 14:00)`. A conflicting reservation `#104` was confirmed for `[tomorrow 11:00, tomorrow 13:00)`.
    - *Action:* Approver triggers `POST /reservations/103/approve`.
    - *Outcome:* Rejected with `CONFLICT_OVERLAP`; `#103` remains `PENDING_APPROVAL`.
- **VE-05-NEG3 (Past Expiration Boundary):**
    - *Setup:* Reservation `#105` in `PENDING_APPROVAL` with `startTime` in the past (`currentTime >= startTime`).
    - *Action:* Approver triggers `POST /reservations/105/approve`.
    - *Outcome:* Rejected with `APPROVAL_WINDOW_EXPIRED`; state transitions to `EXPIRED`.

### Architecture Driver(s) for C03
- **AD-01 (Role-Based Authorization & Security Boundaries):** Enforcement of operational privilege boundaries between standard renters and managerial approvers across both service logic and web endpoints.
- **AD-02 (Asynchronous Time Expiration / TTL):** Mechanism (e.g., background schedulers vs. event-driven sweeps) to transition stale pending reservations to `EXPIRED` without requiring manual page visits.

---

## 14. Update the Running Application Baseline

### Summary of Implementation Adjustments
- **Domain Model:** Extended `ReservationState` with `PENDING_APPROVAL`, `REJECTED`, and `EXPIRED`. Added `requiresApproval` / `luxury` property to `Vehicle`.
- **DTOs:** Adopted `ReservationForm` using separated `LocalDate` and hour integers to eliminate client-side minute picker errors and step mismatches.
- **Domain Service (`ReservationService`):**
    - Implemented `approveReservation(id)` with conflict checks and temporal boundary validations.
    - Implemented `rejectReservation(id)` to terminate unwanted requests.
    - Updated confirmation router to transition to `PENDING_APPROVAL` when reserving approval-gated vehicles.
    - Updated cancellation logic to permit cancelling `PENDING_APPROVAL` records prior to start time.
- **UI / Presentation Layer:**
    - Integrated HTMX attributes (`data-hx-post`, `data-hx-target`) for partial DOM updates without full browser reloads.
    - Added visual badges (`PENDING_APPROVAL`, `REJECTED`, `EXPIRED`) in the reservation tables.
    - Added dedicated approval actions in the management section.

---

## 15. Minimal Evidence for C03 and CP1

### Accepted Baseline
- **Baseline Version:** Baseline v0.2 (Approval Workflow for Luxury & Restricted Fleet Resources).

### Core Operations Demonstrated
1. **OP-01 (Create Reservation):** Persists client booking intent as `DRAFT`.
2. **OP-02 (Check Availability):** Verifies vehicle schedule availability over half-open intervals.
3. **OP-03 (Confirm Reservation):** Converts standard drafts to `CONFIRMED` and routes luxury drafts to `PENDING_APPROVAL`.
4. **OP-04 (Cancel Reservation):** Releases `DRAFT`, `PENDING_APPROVAL`, or `CONFIRMED` bookings prior to start time.
5. **OP-05 (Approve Reservation):** Authorizes luxury bookings and commits exclusive allocation.
6. **OP-06 (Reject Reservation):** Declines luxury bookings and closes requests.

### Verification Examples Actually Executed
- **VE-01-POS & NEG:** Successfully created valid draft; prevented past interval creation.
- **VE-02-POS & NEG:** Confirmed available slot detection; confirmed collision detection on overlapping intervals.
- **VE-03-POS & NEG:** Confirmed standard car reservation; prevented direct confirmation for luxury vehicle (routed to `PENDING_APPROVAL`).
- **VE-04-POS & NEG:** Cancelled reservation with `currentTime < startTime`; rejected cancellation attempt after start time.
- **VE-05-POS & NEG:** Approved pending luxury booking into `CONFIRMED`; rejected approval of expired or conflicting pending reservations.

### Mismatch Found and Resolved
- **Issue:** The browser date-time picker triggered client-side step validation errors (`"Please enter a valid value..."`) and caused full page refreshes that reset vehicle and user selections.
- **Resolution:** Replaced raw `datetime-local` inputs with paired standard date pickers and whole-hour dropdown selectors (`00:00` - `23:00`). Preserved form selections in the controller model and integrated HTMX targeted swaps to prevent unprompted page reloads.

### Change Impact Summary
- Extended lifecycle statechart from 3 states to 6 states (`DRAFT`, `PENDING_APPROVAL`, `CONFIRMED`, `REJECTED`, `CANCELLED`, `EXPIRED`).
- Separated standard confirmation flow from administrative approval workflows.
- Isolated changes so that standard vehicle reservations continue to function identically to Baseline v0.1.

### Remaining Assumption / Unknown
- **Pending Hold Policy:** Does `PENDING_APPROVAL` temporarily reserve the car against standard availability checks (soft lock), or does it remain completely open until the manager executes an approval decision? (Currently treated as open until approved).
- **Concurrency Isolation Strategy:** Resolution of database concurrency locking mechanisms (optimistic locking vs. pessimistic row locks vs. serialized transactions) when multiple confirmations/approvals collide under high load is deferred to C03.

### Architecture Driver(s) Carried into C03
1. **Concurrency Control under Contention (Driver Q - Contention):** Ensuring invariant `BR-02` holds atomically under high-frequency simultaneous approval and confirmation requests.
2. **Role & Privilege Enforcement:** Formalizing security contexts and authorization checks for manager endpoints.
3. **Automated Lifecycle Sweepers:** Transitioning unapproved records to `EXPIRED` automatically upon deadline breach.

### Application Commit / Tag
- **Tag:** `baseline-v0.2`
- **Commit Message:** `feat: implement Baseline v0.2 approval workflow for luxury resources`