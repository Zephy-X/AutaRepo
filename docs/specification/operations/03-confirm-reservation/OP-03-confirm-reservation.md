# OP-03 — Confirm Reservation

## Goal / User Value
User finalizes their booking (Step 3: "Complete Booking"), converting a `DRAFT` reservation into an exclusive, legally binding allocation that locks the car against all conflicting bookings.

## Trigger
User submits a confirmation request for an existing reservation by its `reservation_id`.

## Observable Requirements
**REQ-03:**
The system shall confirm a `DRAFT` Reservation only when the target Resource is active and its interval does not conflict with any existing `CONFIRMED` Reservation on that Resource under `BR-02`.

**REQ-04:**
For concurrent confirmation attempts that conflict under `BR-02`, at most one Reservation shall transition to `CONFIRMED`; all competing requests shall be rejected.

## Preconditions
- Reservation exists.
- `Reservation.state == DRAFT`.
- Target Resource (Car) is active.

## Success Postcondition
- `Reservation.state = CONFIRMED`.
- The Reservation exclusively blocks the car for its interval.
- System invariant `BR-02` remains satisfied.

## State Change
`DRAFT` → `CONFIRMED`

## Referenced Rules
- `BR-01` Interval semantics
- `BR-02` Exclusive Resource invariant
- `BR-04` Draft intent & non-allocation

## Main Success Scenario
1. User submits confirmation request with `reservation_id`.
2. System retrieves Reservation and verifies state is `DRAFT`.
3. System verifies target car is active.
4. System executes conflict detection: checks for any overlapping `CONFIRMED` reservations on the same car.
5. No conflict found: system atomically updates `Reservation.state = CONFIRMED`.
6. System returns the updated Reservation record with state `CONFIRMED`.

## Alternative / Failure Outcomes
- **Unknown Reservation:** Reservation ID not found → rejected with `RESERVATION_NOT_FOUND`.
- **Invalid Source State:** Reservation is already `CONFIRMED` or `CANCELLED` → rejected with `INVALID_STATE_TRANSITION`; state unchanged.
- **Resource Inactive:** Car is disabled or flagged inactive → rejected with `RESOURCE_INACTIVE`; Reservation remains `DRAFT`.
- **Interval Collision:** Another user confirmed an overlapping interval first → rejected with `CONFLICT_OVERLAP`; Reservation remains `DRAFT`.
- **Concurrent Race:** Two users attempt to confirm overlapping drafts at the exact same millisecond → exactly one transitions to `CONFIRMED`, the second is rejected with `CONFLICT_OVERLAP` per `REQ-04`.

## Verification Examples
- **Positive:** Reservation `res_1` is `DRAFT` for `CAR-01` at `[10:00, 12:00)`, no confirmed bookings exist → transitions to `CONFIRMED`.
- **Negative (Overlap Collision):** Reservation `res_2` is `DRAFT` for `CAR-01` at `[11:00, 13:00)`. `res_1` is already `CONFIRMED` for `[10:00, 12:00)` → confirmation of `res_2` rejected with `CONFLICT_OVERLAP`; `res_2` remains `DRAFT`.
- **Negative (Re-confirm):** Confirming `res_1` when it is already `CONFIRMED` → rejected with `INVALID_STATE_TRANSITION`.
- **Negative (Confirm Cancelled):** Confirming `res_3` when its state is `CANCELLED` → rejected with `INVALID_STATE_TRANSITION`.

## Rationale
Acts as the single point of truth for exclusivity. Pushing concurrency enforcement to this final commit boundary allows frictionless drafting while mathematically guaranteeing `BR-02`.

## Assumption / Unknown / TBD
The exact concurrency locking mechanism (database row locks, optimistic versioning, or serializable transactions) is an architectural choice deferred to C03.