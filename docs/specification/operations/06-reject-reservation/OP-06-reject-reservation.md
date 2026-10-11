# OP-06 — Reject Reservation

### Goal / User Value
An authorized Approver (Fleet Manager) declines a pending luxury vehicle reservation, releasing the provisional soft-lock immediately so the vehicle becomes available to others.

### Trigger
Authorized Approver submits a rejection decision with an optional reason for an existing reservation by its `reservation_id`.

### Observable Requirement
* **REQ-07**: The system shall transition a Reservation from `PENDING_APPROVAL` to `REJECTED` only when triggered by an authorized approver prior to trip start time ($\text{currentTime} < \text{Reservation.startTime}$).

### Preconditions
* Reservation exists.
* $\text{Reservation.state} == \text{PENDING\_APPROVAL}$.
* Current system time is strictly before `Reservation.startTime`.
* Acting user has the Approver role.

### Success Postconditions
* $\text{Reservation.state} = \text{REJECTED}$.
* The soft-lock is released immediately.
* Rejection metadata (approver ID, timestamp, rejection reason) is recorded.
* Target interval immediately reflects as `AVAILABLE` in OP-02.
* Notification dispatched to user.

### State Change
`PENDING_APPROVAL` $\rightarrow$ `REJECTED`

### Referenced Rules
* **BR-01**: Interval semantics
* **BR-02**: Exclusive Resource invariant & blocking states
* **BR-05**: Luxury vehicle policy & authorization lifecycle

### Main Success Scenario
1. Approver submits rejection request with `reservation_id` and optional `rejectionReason`.
2. System retrieves Reservation and verifies state is `PENDING_APPROVAL`.
3. System verifies Approver authorization.
4. System verifies $\text{currentTime} < \text{Reservation.startTime}$.
5. System updates $\text{Reservation.state} = \text{REJECTED}$, dissolves the soft-lock, and records rejection metadata.
6. System returns the updated Reservation with state `REJECTED`.

### Alternative / Failure Outcomes
* **Unknown Reservation**: Reservation ID not found $\rightarrow$ rejected with `RESERVATION_NOT_FOUND`.
* **Unauthorized Actor**: Requesting user lacks approver privileges $\rightarrow$ rejected with `ACCESS_DENIED`.
* **Invalid Source State**: Reservation is not in `PENDING_APPROVAL` $\rightarrow$ rejected with `INVALID_STATE_TRANSITION`.
* **Window Closed**: $\text{currentTime} \ge \text{Reservation.startTime} \rightarrow$ rejected with `REJECTION_WINDOW_CLOSED`.