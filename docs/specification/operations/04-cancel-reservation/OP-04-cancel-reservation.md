# OP-04 — Cancel Reservation

## Goal / User Value
User can cancel an unwanted draft or an existing confirmed booking (Step 4: Change of plans), releasing any claimed car allocation back to the pool of available vehicles.

## Trigger
User submits a cancellation request for an existing reservation by its `reservation_id`.

## Observable Requirement
**REQ-05:**
The system shall allow a `DRAFT` or `CONFIRMED` Reservation to be cancelled if and only if the current system time is strictly earlier than the reservation start time (`currentTime < Reservation.start`).

## Preconditions
- Reservation exists.
- `Reservation.state ∈ {DRAFT, CONFIRMED}`.
- `currentTime < Reservation.start` (evaluated against system clock per `BR-03`).

## Success Postcondition
- `Reservation.state = CANCELLED`.
- If the reservation was `CONFIRMED`, the car immediately ceases to be blocked and becomes `AVAILABLE` in `OP-02`.
- System invariant `BR-02` remains satisfied.

## State Change
`DRAFT` → `CANCELLED`  
or  
`CONFIRMED` → `CANCELLED`

## Referenced Rules
- `BR-01` Interval semantics
- `BR-02` Exclusive Resource invariant
- `BR-03` Cancellation policy

## Main Success Scenario
1. User submits cancellation request with `reservation_id`.
2. System retrieves Reservation record.
3. System verifies state is either `DRAFT` or `CONFIRMED`.
4. System verifies temporal constraint: `currentTime < Reservation.start`.
5. System updates `Reservation.state = CANCELLED`.
6. System returns the updated Reservation record with state `CANCELLED`.

## Alternative / Failure Outcomes
- **Unknown Reservation:** Reservation ID not found → rejected with `RESERVATION_NOT_FOUND`.
- **Already Cancelled:** Reservation state is already `CANCELLED` → rejected with `ALREADY_CANCELLED`; state unchanged per `BR-03`.
- **Trip Already Started / Passed:** `currentTime >= Reservation.start` → rejected with `CANCELLATION_WINDOW_CLOSED`; state unchanged.
- **Race Condition with Confirm:** If a cancel and confirm command race, atomic evaluation ensures exactly one state transition wins without leaving inconsistent locks.

## Verification Examples
*Given Reservation res_1 for CAR-01 at [2026-10-01 10:00, 2026-10-01 14:00)*
- **Positive (Draft before start):** `currentTime = 2026-10-01 08:00`, state `DRAFT` → transitions to `CANCELLED`.
- **Positive (Confirmed before start):** `currentTime = 2026-10-01 08:00`, state `CONFIRMED` → transitions to `CANCELLED`; `OP-02` now reports `CAR-01` as `AVAILABLE` for `[10:00, 14:00)`.
- **Negative (After start):** `currentTime = 2026-10-01 10:05`, state `CONFIRMED` → rejected with `CANCELLATION_WINDOW_CLOSED`.
- **Negative (At exact boundary):** `currentTime = 2026-10-01 10:00:00`, state `CONFIRMED` → rejected with `CANCELLATION_WINDOW_CLOSED` (strictly `<` required).
- **Negative (Repeated cancel):** Submitting cancel for a reservation that is already `CANCELLED` → rejected with `ALREADY_CANCELLED`.

## Rationale
Supports Step 4 of the user journey: abandoning a basket or cancelling a trip in advance, while preventing retroactive cancellations once the booking window begins.

## Assumption / Unknown / TBD
No cancellation fee, refund computation, or partial-use cancellation is supported in Baseline v0.1.