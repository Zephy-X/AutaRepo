# Project Frame

## Reservation domain

Shared vehicle / car reservation fleet.

## Purpose

A web-based reservation platform for individuals and organizations to check availability and reserve vehicles. It eliminates scheduling conflicts and guarantees availability for scheduled pickup windows.

## Users / Stakeholders

- **User / Renter**: Browses inventory, drafts bookings, confirms reservations, and cancels upcoming trips.
- **Fleet Manager**: Oversees operational vehicle statuses (`isActive`). (Authentication, role enforcement, and approval gates are deferred to future milestones).

## Core concepts

- **Resource (Vehicle)**: The car being reserved (`id`, `brand`, `model`, `licensePlate`, `isActive`, `requiresApproval`).
- **Reservation**: The reservation record with a half-open time range `[startTime, endTime)`, links to Resource and User, and an operational lifecycle status.
- **User**: The client creating and interacting with reservations (represented as an unauthenticated profile reference in Baseline v0.1).
- **ReservationState**: Lifecycle enum (`DRAFT`, `CONFIRMED`, `CANCELLED`).

## Core operations (Baseline v0.1)

- **OP-01 Create Reservation**: User records booking intent by creating a new reservation in `DRAFT` state for a chosen vehicle. No allocation is committed.
- **OP-02 Check Availability**: Read-only query evaluating whether an exclusive vehicle is available for a requested half-open interval `[start, end)`.
- **OP-03 Confirm Reservation**: Converts an eligible `DRAFT` into a committed, binding allocation (`CONFIRMED`), exclusively locking the vehicle and preventing double-booking.
- **OP-04 Cancel Reservation**: Withdraws a `DRAFT` or `CONFIRMED` reservation prior to its start time (`currentTime < startTime`), transitioning it to `CANCELLED` and freeing allocated inventory.
- **Manage Fleet (Resource Admin)**: Minimal maintenance operation to mark vehicles as active or out of service (`isActive = true/false`).

## Persistent state

- **Vehicle**: `id` (PK), `brand`, `model`, `vin`, `licensePlate`, `isActive` (boolean flag denoting operational status), `requiresApproval` (boolean flag).
- **User**: `id` (PK), `fullName`, `email`.
- **Reservation**: `id` (PK), `vehicle_id` (FK), `user_id` (FK), `startTime` (timestamp), `endTime` (timestamp), `state` (enum: `DRAFT`, `CONFIRMED`, `CANCELLED`), `createdAt` (timestamp).

## State-changing operations

- `[none]` → `DRAFT` (`OP-01` Create Reservation)
- `DRAFT` → `CONFIRMED` (`OP-03` Confirm Reservation, enforces exclusivity check under `BR-02`)
- `DRAFT` → `CANCELLED` (`OP-04` Cancel Reservation)
- `CONFIRMED` → `CANCELLED` (`OP-04` Cancel Reservation, releases vehicle allocation)

## Common business rules & invariants

- **BR-01 (Interval Semantics)**: All reservation periods use half-open interval semantics `[startTime, endTime)`. An adjacent booking starting at `endTime` does not collide.
- **BR-02 (Exclusive Resource Invariant)**: At no committed system state may two `CONFIRMED` reservations overlap for the exact same vehicle:
  $$\max(A.start, B.start) < \min(A.end, B.end)$$
- **BR-03 (Cancellation Policy)**: A reservation may be cancelled if and only if `currentTime < Reservation.startTime`.
- **BR-04 (Draft Non-Allocation)**: A `DRAFT` reservation does not claim or block vehicle availability in `OP-02`.

## Domain-specific business rule

- **BR-Domain (Lead Time & Max Duration)**: A reservation cannot be created in the past (`startTime > currentTime`), and the reservation duration must strictly satisfy `0 < (endTime - startTime) <= 14 days`.

## External / system boundary

- **Notification Service**: External messaging boundary notified when a reservation transitions to `CONFIRMED` or `CANCELLED`. For Baseline v0.1, this is modeled as an internal mock/stub interface.
- **System Clock**: External source of truth for current system time used by temporal invariants in `OP-01` and `OP-04`.

## Assumptions

- Authentication and fine-grained authorization are out of scope for Baseline v0.1; client requests directly invoke system operations.
- Draft reservations do not auto-expire via background TTL in Baseline v0.1; they persist until confirmed or explicitly cancelled.
- Standard UTC timestamps are used for all temporal comparisons, with negligible server clock drift.

## Unknowns

- Technical concurrency isolation strategy (database row-level locking vs. optimistic versioning vs. exclusion constraints) during concurrent confirmation attempts on the same vehicle. Deferred as an architectural driver for C03.

---

## Selected future pressure

- **Category**: Q (Quality / Concurrency & Contention)
- **Concrete pressure**: High-concurrency confirmation attempts on the same popular vehicle for overlapping time slots during peak hours.
- **Relevance to our system**: Multiple users can hold coexisting `DRAFT` reservations for the same vehicle. When several users submit `OP-03 Confirm Reservation` simultaneously, race conditions could cause double-booking unless atomic isolation and strict invariant checks (`REQ-04`) are engineered into the architecture in C03.