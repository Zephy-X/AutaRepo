# Project Frame (Baseline v0.2)

## Reservation domain

Shared vehicle / car reservation fleet with tiered luxury/restricted approval governance.

## Purpose

A web-based reservation platform for individuals and organizations to check availability and reserve vehicles. It eliminates scheduling conflicts, guarantees exclusive availability for scheduled pickup windows, and routes restricted/luxury vehicle requests through an authorized approval gate before allocation commitment.

## Users / Stakeholders

- **User / Renter**: Browses inventory, drafts bookings, submits reservations for confirmation or approval, and cancels upcoming trips.
- **Fleet Manager / Approver**: Oversees operational vehicle statuses (`isActive`) and reviews, approves, or rejects pending requests for high-tier/luxury resources (`requiresApproval`).

## Core concepts

- **Resource (Vehicle)**: The car being reserved (`id`, `brand`, `model`, `licensePlate`, `isActive`, `requiresApproval` / luxury classification).
- **Reservation**: The reservation record with a half-open time range `[startTime, endTime)`, links to Resource and User, and an operational lifecycle status.
- **User**: The client creating and interacting with reservations (includes approver identity references for managerial actions).
- **ReservationState**: Lifecycle enum (`DRAFT`, `PENDING_APPROVAL`, `CONFIRMED`, `REJECTED`, `CANCELLED`, `EXPIRED`).

## Core operations (Baseline v0.2)

- **OP-01 Create Reservation**: User records booking intent by creating a new reservation in `DRAFT` state for a chosen vehicle. No allocation is committed.
- **OP-02 Check Availability**: Read-only query evaluating whether an exclusive vehicle is available for a requested half-open interval `[start, end)`.
- **OP-03 Confirm Reservation**: Converts an eligible standard `DRAFT` into a committed, binding allocation (`CONFIRMED`). For vehicles requiring approval (`requiresApproval == true`), transitions the reservation to `PENDING_APPROVAL`.
- **OP-04 Cancel Reservation**: Withdraws a `DRAFT`, `PENDING_APPROVAL`, or `CONFIRMED` reservation prior to its start time (`currentTime < startTime`), transitioning it to `CANCELLED` and freeing held/allocated inventory.
- **OP-05 Approve Reservation**: An authorized Approver reviews a `PENDING_APPROVAL` reservation and converts it to a committed allocation (`CONFIRMED`), exclusively locking the vehicle under `BR-02`.
- **OP-06 Reject Reservation**: An authorized Approver declines a `PENDING_APPROVAL` request, transitioning it to `REJECTED`.
- **Manage Fleet (Resource Admin)**: Maintenance operations to mark vehicles as active/inactive (`isActive = true/false`) and configure authorization requirements (`requiresApproval = true/false`).

## Persistent state

- **Vehicle**: `id` (PK), `brand`, `model`, `vin`, `licensePlate`, `isActive` (boolean), `requiresApproval` (boolean flag denoting approval gate requirement).
- **User**: `id` (PK), `fullName`, `email`, `role` (enum / identifier for approver privileges).
- **Reservation**: `id` (PK), `vehicle_id` (FK), `user_id` (FK), `startTime` (timestamp), `endTime` (timestamp), `state` (enum: `DRAFT`, `PENDING_APPROVAL`, `CONFIRMED`, `REJECTED`, `CANCELLED`, `EXPIRED`), `approver_id` (FK, nullable), `approvedAt` (timestamp, nullable), `createdAt` (timestamp).

## State-changing operations

- `[none]` → `DRAFT` (`OP-01` Create Reservation)
- `DRAFT` → `CONFIRMED` (`OP-03` Confirm Reservation, standard resources only, enforces `BR-02`)
- `DRAFT` → `PENDING_APPROVAL` (`OP-03` Confirm Reservation, triggered when `vehicle.requiresApproval == true`)
- `PENDING_APPROVAL` → `CONFIRMED` (`OP-05` Approve Reservation, executed by Approver, enforces `BR-02`)
- `PENDING_APPROVAL` → `REJECTED` (`OP-06` Reject Reservation, executed by Approver)
- `PENDING_APPROVAL` → `EXPIRED` (System timeout when `currentTime >= startTime` without an approval decision)
- `DRAFT` → `CANCELLED` (`OP-04` Cancel Reservation, user cancellation)
- `PENDING_APPROVAL` → `CANCELLED` (`OP-04` Cancel Reservation, user cancellation prior to start time)
- `CONFIRMED` → `CANCELLED` (`OP-04` Cancel Reservation, releases vehicle allocation)

## Common business rules & invariants

- **BR-01 (Interval Semantics)**: All reservation periods use half-open interval semantics `[startTime, endTime)`. An adjacent booking starting at `endTime` does not collide.
- **BR-02 (Exclusive Resource Invariant)**: At no committed system state may two `CONFIRMED` reservations overlap for the exact same vehicle:

  $$\max(A.start, B.start) < \min(A.end, B.end)$$
- **BR-03 (Cancellation Policy)**: A reservation may be cancelled if and only if `currentTime < Reservation.startTime`.
- **BR-04 (Draft Non-Allocation)**: A `DRAFT` reservation does not claim or block vehicle availability in `OP-02`.
- **BR-05 (Restricted/Luxury Approval Requirement)**: A vehicle with `requiresApproval == true` cannot transition directly from `DRAFT` to `CONFIRMED`. It must transition to `PENDING_APPROVAL` and receive explicit sign-off via `OP-05`.
- **BR-06 (Approval Window & Expiration)**: A reservation in `PENDING_APPROVAL` can only be approved while `currentTime < Reservation.startTime`. If this window lapses, the reservation is no longer actionable and transitions to `EXPIRED`.

## Domain-specific business rule

- **BR-Domain (Lead Time & Max Duration)**: A reservation cannot be created in the past (`startTime > currentTime`), and the reservation duration must strictly satisfy `0 < (endTime - startTime) <= 14 days`.

## External / system boundary

- **Notification Service**: External messaging boundary notified when a reservation transitions to `PENDING_APPROVAL`, `CONFIRMED`, `REJECTED`, or `CANCELLED`.
- **System Clock**: External source of truth for current system time used for temporal invariants in `OP-01`, `OP-04`, `OP-05`, and expiration evaluations.

## Assumptions

- Authentication and full enterprise role-based access control (RBAC) infrastructure are simplified; operational actors are distinguished via profile flags or explicit request contexts.
- Immediate evaluation is used for expiration checks (e.g., lazy evaluation upon access or submission attempt), deferring background cron sweeps to architecture phase C03.
- Standard UTC timestamps are used for all temporal comparisons.

## Unknowns

- **Concurrency Isolation Mechanism**: Strategy for row locks, optimistic concurrency controls, or serializable transactions during simultaneous approvals and confirmations under high load.
- **Pending Hold Semantics**: Whether `PENDING_APPROVAL` holds should soft-block `OP-02 Availability` to prevent competitor drafting, or remain open until explicit confirmation commits under `BR-02`.

## Selected future pressure

- **Category**: Q (Quality / Concurrency, Contention & Temporal Consistency)
- **Concrete pressure**: Race conditions between competing approvals and direct confirmations on shared or luxury fleet inventories, paired with stale `PENDING_APPROVAL` records expiring at pickup windows.
- **Relevance to our system**: Introducing approval workflows creates asynchronous delay between user submission and allocation commitment. If multiple managers or concurrent users attempt to approve or confirm overlapping slots at the last second, robust concurrency locks and atomic status checks (`REQ-04`, `REQ-06`) must be implemented in C03 to prevent double-booking or approving expired records.
