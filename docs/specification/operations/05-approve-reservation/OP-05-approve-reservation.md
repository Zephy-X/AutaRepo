# OP-05 — Approve Reservation

### Goal / User Value
An authorized Approver (Fleet Manager) approves a pending luxury vehicle reservation, converting the existing exclusive soft-lock into a binding, confirmed allocation.

### Trigger
Authorized Approver submits an approval decision for an existing reservation by its `reservation_id`.

### Observable Requirement
* **REQ-06**: The system shall transition a Reservation from `PENDING_APPROVAL` to `CONFIRMED` only when triggered by an authorized approver, the target Resource is active, and current system time is strictly prior to `Reservation.startTime` ($\text{currentTime} < \text{Reservation.startTime}$).

### Preconditions
* Reservation exists.
* $\text{Reservation.state} == \text{PENDING\_APPROVAL}$.
* Target Resource (Car) is active.
* Current system time is strictly before `Reservation.startTime`.
* Acting user has the Approver role.

### Success Postconditions
* $\text{Reservation.state} = \text{CONFIRMED}$.
* The soft-lock is converted into a permanent hard-allocation.
* Approver identity (`approver_id`) and approval timestamp (`approvedAt`) are recorded.
* Notification sent to user.

### State Change
`PENDING_APPROVAL` $\rightarrow$ `CONFIRMED`

### Referenced Rules
* **BR-01**: Interval semantics
* **BR-02**: Exclusive Resource invariant & blocking states
* **BR-05**: Luxury vehicle policy & authorization lifecycle
* **BR-06**: Time-to-live & automatic expiration

### Main Success Scenario
1. Approver submits approval request with `reservation_id`.
2. System retrieves Reservation and verifies state is `PENDING_APPROVAL`.
3. System verifies Approver credentials and target car active status.
4. System verifies that $\text{currentTime} < \text{Reservation.startTime}$.
5. System atomically updates $\text{Reservation.state} = \text{CONFIRMED}$, sets `approver_id` and `approvedAt`.
6. System returns updated Reservation with state `CONFIRMED`.

### Alternative / Failure Outcomes
* **Unknown Reservation**: Reservation ID not found $\rightarrow$ rejected with `RESERVATION_NOT_FOUND`.
* **Unauthorized Actor**: Requesting user lacks approver role $\rightarrow$ rejected with `ACCESS_DENIED`.
* **Invalid Source State**: Reservation is in any state other than `PENDING_APPROVAL` $\rightarrow$ rejected with `INVALID_STATE_TRANSITION`.
* **Window Expired**: $\text{currentTime} \ge \text{Reservation.startTime}$ or $\text{currentTime} \ge \text{reviewDeadline} \rightarrow$ rejected with `APPROVAL_WINDOW_EXPIRED` (triggers transition to `EXPIRED`).
* **Resource Inactive**: Target car has been disabled $\rightarrow$ rejected with `RESOURCE_INACTIVE`; Reservation remains `PENDING_APPROVAL`.
