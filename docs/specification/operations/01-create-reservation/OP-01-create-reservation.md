# OP-01 — Create Reservation

## Goal / User Value
User creates a reservation draft (putting a selected car in the checkout basket) to record their booking intent without prematurely locking the car against other browsing users.

## Trigger
User submits a draft booking request for Resource (Car) R and interval I = [start, end).

## Observable Requirement
**REQ-01:**
The system shall create a `DRAFT` Reservation for an existing Resource when the requested interval satisfies `start < end`.

## Preconditions
- Target Resource (Car) exists.
- Requested interval is valid (`start < end` per `BR-01`).

## Success Postcondition
- Exactly one new Reservation entity exists in the system.
- `Reservation.state = DRAFT`.
- No car allocation is committed (`BR-04` applies; the car remains available to others).
- The unique Reservation ID, car ID, interval, and state `DRAFT` are returned to the user.

## State Change
`[none]` → `DRAFT`

## Referenced Rules
- `BR-01` Interval semantics
- `BR-04` Draft intent & non-allocation

## Main Success Scenario
1. User provides `resource_id` (Car) and interval `[start, end)`.
2. System validates that the Resource exists.
3. System validates interval syntax (`start < end`).
4. System creates Reservation with state set to `DRAFT`.
5. System returns the Reservation identifier, car identifier, interval, and state `DRAFT`.

## Alternative / Failure Outcomes
- **Unknown Resource:** Car does not exist → rejected with `RESOURCE_NOT_FOUND`; no Reservation created.
- **Invalid Interval (`start >= end`):** Bounds inverted or equal → rejected with `INVALID_INTERVAL`; no Reservation created.
- **Interval in Past (`start < currentTime`):** Booking starts in past → rejected with `INTERVAL_IN_PAST`; no Reservation created.

## Verification Examples
- **Positive:** Car `CAR-01` exists, interval `[10:00, 14:00)` → Reservation created with ID `res_101`, state `DRAFT`.
- **Negative (Zero duration):** `[10:00, 10:00)` → rejected with `INVALID_INTERVAL`.
- **Negative (Inverted interval):** `[14:00, 10:00)` → rejected with `INVALID_INTERVAL`.
- **Negative (Car does not exist):** Query for car `CAR-999` → rejected with `RESOURCE_NOT_FOUND`.
- **Edge (Coexisting Drafts):** User A creates draft for `CAR-01` at `[10:00, 14:00)`; User B simultaneously creates draft for `CAR-01` at `[11:00, 15:00)` → both succeed with state `DRAFT` (`DRAFT` does not lock inventory).

## Rationale
Matches Step 2 of the booking journey: placing a vehicle in the basket. Intent is preserved without creating denial-of-inventory or artificial deadlocks for other users.

## Assumption / Unknown / TBD
- Authorization and user roles are out of scope for Baseline v0.1.
- Draft expiration timer (TTL) is not implemented in v0.1; drafts persist until confirmed or cancelled.