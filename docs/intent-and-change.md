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
