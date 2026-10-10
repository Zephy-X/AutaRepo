# OP-04 — Cancel Reservation

### Goal / User Value
User or Approver cancels an unwanted draft, pending approval request, or confirmed booking prior to trip start time, immediately releasing any soft- or hard-lock back to the fleet inventory.

### Trigger
User or Approver submits a cancellation request for an existing reservation by its `reservation_id`.

### Observable Requirement
* **REQ-05**: The system shall allow a Reservation in state `DRAFT`, `PENDING_APPROVAL`, or `CONFIRMED` to be cancelled if and only if the current system time is strictly earlier than the reservation start time ($\text{currentTime} < \text{Reservation.startTime}$).

### Preconditions
* Reservation exists.
* $\text{Reservation.state} \in \{\text{DRAFT}, \text{PENDING\_APPROVAL}, \text{CONFIRMED}\}$.
* $\text{currentTime} < \text{Reservation.startTime}$ (evaluated against system clock per BR-03).

### Success Postconditions
* $\text{Reservation.state} = \text{CANCELLED}$.
* Any hard-lock or soft-lock on the vehicle is immediately released.
* OP-02 reflects the interval as `AVAILABLE`.
* System invariant BR-02 remains satisfied.

### State Change
* `DRAFT` $\rightarrow$ `CANCELLED`
* `PENDING_APPROVAL` $\rightarrow$ `CANCELLED`
* `CONFIRMED` $\rightarrow$ `CANCELLED`

### Referenced Rules
* **BR-01**: Interval semantics
* **BR-02**: Exclusive Resource invariant & blocking states
* **BR-03**: Cancellation policy

### Main Success Scenario
1. Actor submits cancellation request with `reservation_id`.
2. System retrieves Reservation record.
3. System verifies state is `DRAFT`, `PENDING_APPROVAL`, or `CONFIRMED`.
4. System verifies temporal constraint: $\text{currentTime} < \text{Reservation.startTime}$.
5. System updates $\text{Reservation.state} = \text{CANCELLED}$ and releases any held lock.
6. System returns the updated Reservation record with state `CANCELLED`.

### Alternative / Failure Outcomes
* **Unknown Reservation**: Reservation ID not found $\rightarrow$ rejected with `RESERVATION_NOT_FOUND`.
* **Terminal State / Re-cancellation**: Reservation state is already `CANCELLED`, `REJECTED`, or `EXPIRED` $\rightarrow$ rejected with `INVALID_STATE_TRANSITION`.
* **Trip Already Started / Passed**: $\text{currentTime} \ge \text{Reservation.startTime} \rightarrow$ rejected with `CANCELLATION_WINDOW_CLOSED`; state unchanged.