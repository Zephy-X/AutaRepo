# Project Frame

## Reservation domain

Shared vehicle / car reservation fleet.

## Purpose

A web-based reservation platform for individuals and organizations to check availability and reserve vehicles. It eliminates scheduling conflicts and guarantees availability for scheduled pickup windows.

## Users / Stakeholders

- **Renter / Driver**: Reserves vehicles for specific time slots.
- **Fleet Manager**: Oversees vehicle states, confirms bookings, and manages availability.

## Core concepts

- **Resource (Vehicle)**: The car being reserved (VIN, brand, model, license plate).
- **Reservation**: The reservation record with a time range, links to Resource and User, and an operational status.
- **User**: The actor creating and using the reservation.
- **ReservationState**: Lifecycle enum (`DRAFT`, `CONFIRMED`, `CANCELLED`).

## Core operations

- **Manage Fleet (Resource CRUD)**: View list of all vehicles, inspect details, add a new vehicle, edit status (Available / Under Maintenance), and remove a vehicle.
- **Create reservation**: Initialize a new reservation in `DRAFT` state for a chosen vehicle.
- **Confirm / approve reservation**: Transition state from `DRAFT` to `CONFIRMED`.
- **Cancel reservation**: Transition state to `CANCELLED`, freeing up the resource slot.
- **Check availability**: Filter the vehicle catalog to see which cars are currently free or inspect upcoming reservations for a specific vehicle.

## Persistent state

- **Vehicle**: `id`, `brand`, `model`, `vin`, `licensePlate`, `isAvailable`.
- **User**: `id`, `fullName`, `email`, `driverLicenseNumber`.
- **Reservation**: `id`, `vehicle_id`, `user_id`, `startTime`, `endTime`, `state`, `createdAt`.

## State-changing operation

`DRAFT` → `CONFIRMED` (enforces availability check and triggers notification stub).

## Common business rule

Confirmed reservations for the same resource (`Vehicle`) must not overlap in their `[startTime, endTime]` period.

## Domain-specific business rule

A reservation must be created at least 2 hours prior to the requested `startTime`, and the reservation duration cannot exceed 14 consecutive days.

## External / system boundary

**Notification Service**: An external messaging boundary notified when a reservation changes state (e.g., `CONFIRMED`, `CANCELLED`). For C01/C02, this is modeled as a mocked service interface.

## Assumption

We assume that clock drift across services is negligible and standard UTC timestamps suffice for all reservation interval checks.

## Unknown

How to cleanly handle database-level locking (pessimistic vs. optimistic) during high-concurrency reservation attempts on the same vehicle.

---

## Selected future pressure

- **Category**: Q (Quality / Scale)
- **Concrete pressure**: 10× increase in concurrent reservation creation attempts on the same popular vehicle during peak hours.
- **Why it is relevant to our reservation system**: If two users attempt to confirm a reservation for the exact same vehicle slot simultaneously, race conditions can produce double bookings unless concurrent validation and transactional isolation are properly engineered.
