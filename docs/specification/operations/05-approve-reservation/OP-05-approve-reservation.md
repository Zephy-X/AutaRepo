# OP-05 — Approve Reservation

## Goal / User Value
An authorized person (e.g., Fleet Manager) reviews and approves a high-tier or restricted vehicle reservation that requires explicit authorization, converting a `PENDING_APPROVAL` request into an exclusive, legally binding allocation that locks the resource against conflicting bookings.

## Trigger
Authorized person submits an approval decision for an existing reservation by its `reservation_id`.

## Observable Requirements
**REQ-05:**
The system shall transition a Reservation from `PENDING_APPROVAL` to `CONFIRMED` only when triggered by an authorized role, the target Resource is active, current system time is strictly prior to `Reservation.startTime`, and the requested interval does not conflict with any existing `CONFIRMED` Reservation on that Resource under `BR-02`.

**REQ-06:**
If a conflicting `CONFIRMED` Reservation already exists for the requested interval, or if the current system time is greater than or equal to `Reservation.startTime`, the approval request shall be rejected and the Reservation shall not transition to `CONFIRMED`.

## Preconditions
- Reservation exists.
- `Reservation.state == PENDING_APPROVAL`.
- Target Resource (Car) is active.
- Current system time is before `Reservation.startTime` (`currentTime < Reservation.startTime`).
- Acting user possesses an authorized approver role.

## Success Postcondition
- `Reservation.state = CONFIRMED`.
- The Reservation exclusively blocks the car for its interval.
- Approver identity and approval timestamp are recorded.
- System invariant `BR-02` remains satisfied.

## State Change
`PENDING_APPROVAL` → `CONFIRMED`

## Referenced Rules
- `BR-01` Interval semantics
- `BR-02` Exclusive Resource invariant
- `BR-04` Draft intent & non-allocation
- `BR-05` Restricted/Luxury approval requirement
- `BR-06` Approval window validity & expiration semantics

## Main Success Scenario
1. Authorized person submits an approval request with `reservation_id`.
2. System retrieves Reservation and verifies state is `PENDING_APPROVAL`.
3. System verifies target car is active.
4. System verifies that `currentTime < Reservation.startTime`.
5. System executes conflict detection: checks for any overlapping `CONFIRMED` reservations on the same car under `BR-02`.
6. No conflict found: system atomically updates `Reservation.state = CONFIRMED` and registers approval metadata.
7. System returns the updated Reservation record with state `CONFIRMED`.

## Alternative / Failure Outcomes
- **Unknown Reservation:** Reservation ID not found → rejected with `RESERVATION_NOT_FOUND`.
- **Unauthorized Actor:** Requesting user lacks approver role → rejected with `ACCESS_DENIED`; state unchanged.
- **Invalid Source State:** Reservation is in any state other than `PENDING_APPROVAL` (e.g., `DRAFT`, `CONFIRMED`, `CANCELLED`) → rejected with `INVALID_STATE_TRANSITION`; state unchanged.
- **Approval Window Expired:** System time is equal to or past `Reservation.startTime` → rejected with `APPROVAL_WINDOW_EXPIRED`; Reservation transitions to `EXPIRED` (or remains unconfirmed).
- **Resource Inactive:** Target car has been disabled or flagged inactive → rejected with `RESOURCE_INACTIVE`; Reservation remains `PENDING_APPROVAL`.
- **Interval Collision:** Another reservation for the same car was confirmed during the pending period → rejected with `CONFLICT_OVERLAP`; Reservation remains `PENDING_APPROVAL`.

## Verification Examples
- **Positive:** Reservation `res_10` is in `PENDING_APPROVAL` for luxury car `LUX-01` at `[tomorrow 10:00, tomorrow 14:00)`. Approver submits approval today → transitions to `CONFIRMED`.
- **Negative (Interval Collision):** Reservation `res_11` is in `PENDING_APPROVAL` for `LUX-01` at `[tomorrow 12:00, tomorrow 16:00)`. A prior booking `res_10` was already confirmed for `[tomorrow 10:00, tomorrow 14:00)` → approval of `res_11` rejected with `CONFLICT_OVERLAP`; `res_11` remains `PENDING_APPROVAL`.
- **Negative (Window Expired):** Reservation `res_12` is in `PENDING_APPROVAL` for `[today 09:00, today 11:00)`. Approver attempts approval at `today 09:05` → rejected with `APPROVAL_WINDOW_EXPIRED`.
- **Negative (Invalid State):** Approving `res_13` whose state is currently `DRAFT` or `CANCELLED` → rejected with `INVALID_STATE_TRANSITION`.

## Rationale
Restricted resources (such as executive or luxury fleet units) represent high-value inventory requiring human governance. Delaying confirmation until managerial sign-off avoids unauthorized allocations while preserving interval exclusivity guarantees under `BR-02`.

## Assumption / Unknown / TBD
- Automatic transition to `EXPIRED` can be executed eagerly during an approval attempt, or asynchronously via a scheduled background sweeper.
- Whether a rejected request transitions to `REJECTED` via a dedicated separate operation (`OP-06 — Reject Reservation`) or an alternative outcome of this slice.