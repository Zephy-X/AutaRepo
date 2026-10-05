# OP-02 — Check Availability

## Goal / User Value
User can browse and query whether a specific car is available for a desired time interval before initiating a booking.

## Trigger
User queries availability for Resource (Car) R and interval I = [start, end).

## Observable Requirement
**REQ-02:**
For a valid interval and existing Resource, the system shall report `UNAVAILABLE` if the interval overlaps any `CONFIRMED` Reservation on that Resource; otherwise it shall report `AVAILABLE`.

## Preconditions
- Target Resource (Car) exists.
- Requested interval is valid (`start < end` per `BR-01`).

## Success Postcondition
- An availability result (`AVAILABLE` or `UNAVAILABLE`) is returned.
- No system entity is created or modified (pure read-only query).

## State Change
None.

## Referenced Rules
- `BR-01` Interval semantics
- `BR-02` Exclusive Resource invariant
- `BR-04` Draft intent & non-allocation

## Main Success Scenario
1. User submits `resource_id` (Car) and interval `[start, end)`.
2. System validates that the car exists.
3. System validates that `start < end`.
4. System queries all `CONFIRMED` reservations for this car overlapping `[start, end)` per `BR-01` and `BR-02`.
5. If at least one overlapping `CONFIRMED` reservation is found, system returns `UNAVAILABLE`.
6. If no overlapping `CONFIRMED` reservation exists, system returns `AVAILABLE`.

## Alternative / Failure Outcomes
- **Unknown Resource:** Car does not exist → rejected with `RESOURCE_NOT_FOUND`.
- **Invalid Interval (`start >= end`):** Bounds inverted or equal → rejected with `INVALID_INTERVAL`.

## Verification Examples
*Given existing CONFIRMED booking for CAR-01: `[10:00, 12:00)`*
- Query `[08:00, 10:00)` → `AVAILABLE` (adjacent boundary touches at 10:00, no overlap under half-open `[start, end)`).
- Query `[10:30, 11:30)` → `UNAVAILABLE` (fully interior overlap).
- Query `[12:00, 14:00)` → `AVAILABLE` (adjacent boundary touches at 12:00, no overlap).
- Query `[09:00, 13:00)` → `UNAVAILABLE` (interval encloses confirmed booking).

*Given existing DRAFT reservation for CAR-01: `[14:00, 16:00)`*
- Query `[14:00, 16:00)` → `AVAILABLE` (`DRAFT` does not block availability per `BR-04`).

## Rationale
Supports Step 1 of the user journey: browsing available inventory fast without acquiring locks or creating database side effects.

## Assumption / Unknown / TBD
None.