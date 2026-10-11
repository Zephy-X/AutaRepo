# OP-03 — Confirm Reservation

### Goal / User Value
User finalizes their booking by submitting a `DRAFT` reservation. For standard cars, this commits an immediate hard-lock (`CONFIRMED`); for luxury cars requiring approval, this establishes an exclusive soft-lock (`PENDING_APPROVAL`) while awaiting management review.

### Trigger
User submits a confirmation request for an existing reservation by its `reservation_id`.

### Observable Requirements
* **REQ-03**: The system shall transition a `DRAFT` Reservation only when the target Resource is active and its interval does not conflict with any existing `CONFIRMED` or `PENDING_APPROVAL` Reservation on that Resource under BR-02.
* **REQ-04**: If the target Resource has $\text{requiresApproval} == \text{false}$, the Reservation shall transition directly to `CONFIRMED`. If $\text{requiresApproval} == \text{true}$, the Reservation shall transition to `PENDING_APPROVAL` with `reviewDeadline` computed per BR-06.

### Preconditions
* Reservation exists.
* $\text{Reservation.state} == \text{DRAFT}$.
* Target Resource (Car) is active.
* Current system time is strictly prior to reservation start ($\text{currentTime} < \text{Reservation.startTime}$).

### Success Postconditions
* For standard vehicles ($\text{requiresApproval} == \text{false}$): $\text{Reservation.state} = \text{CONFIRMED}$, hard-lock committed.
* For luxury vehicles ($\text{requiresApproval} == \text{true}$): $\text{Reservation.state} = \text{PENDING\_APPROVAL}$, exclusive soft-lock committed, `reviewDeadline` set, notification dispatched.
* System invariant BR-02 remains satisfied.

### State Change
* Standard: `DRAFT` $\rightarrow$ `CONFIRMED`
* Luxury / Requires Approval: `DRAFT` $\rightarrow$ `PENDING_APPROVAL`

### Referenced Rules
* **BR-01**: Interval semantics
* **BR-02**: Exclusive Resource invariant & blocking states
* **BR-04**: Draft intent & non-allocation
* **BR-05**: Luxury vehicle policy & authorization lifecycle
* **BR-06**: Time-to-live & automatic expiration

### Main Success Scenario
1. User submits confirmation request with `reservation_id`.
2. System retrieves Reservation and verifies state is `DRAFT`.
3. System verifies target car is active and $\text{currentTime} < \text{Reservation.startTime}$.
4. System executes conflict detection: checks for any overlapping `CONFIRMED` or `PENDING_APPROVAL` reservations on the same car under BR-02.
5. No conflict found:
    * **Case A (Standard)**: System atomically transitions $\text{Reservation.state} = \text{CONFIRMED}$.
    * **Case B (Luxury)**: System computes $\text{reviewDeadline} = \min(\text{currentTime} + \text{SLA}, \text{Reservation.startTime})$ and atomically transitions $\text{Reservation.state} = \text{PENDING\_APPROVAL}$.
6. System returns the updated Reservation record.

### Alternative / Failure Outcomes
* **Unknown Reservation**: Reservation ID not found $\rightarrow$ rejected with `RESERVATION_NOT_FOUND`.
* **Invalid Source State**: Reservation is not in `DRAFT` $\rightarrow$ rejected with `INVALID_STATE_TRANSITION`.
* **Resource Inactive**: Car is disabled or inactive $\rightarrow$ rejected with `RESOURCE_INACTIVE`; Reservation remains `DRAFT`.
* **Interval Collision**: Another reservation for the same car is already `CONFIRMED` or `PENDING_APPROVAL` for an overlapping window $\rightarrow$ rejected with `CONFLICT_OVERLAP`; Reservation remains `DRAFT`.