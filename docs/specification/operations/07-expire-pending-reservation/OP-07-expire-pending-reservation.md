# OP-07 — Expire Pending Reservation

### Goal / User Value
Automated system background process ensures luxury vehicle requests do not hold inventory indefinitely if left unreviewed by administrators, restoring vehicles to the booking pool upon timeout.

### Trigger
Automated scheduler cron execution, background worker sweep, or access check when:
$$\text{currentTime} \ge \text{Reservation.reviewDeadline} \quad \lor \quad \text{currentTime} \ge \text{Reservation.startTime}$$

### Observable Requirement
* **REQ-08**: The system shall automatically transition any Reservation in `PENDING_APPROVAL` to `EXPIRED` when current system time reaches or exceeds its `reviewDeadline` or `startTime`.

### Preconditions
* Reservation exists.
* $\text{Reservation.state} == \text{PENDING\_APPROVAL}$.
* Condition satisfied: $\text{currentTime} \ge \text{Reservation.reviewDeadline} \lor \text{currentTime} \ge \text{Reservation.startTime}$.

### Success Postconditions
* $\text{Reservation.state} = \text{EXPIRED}$.
* The soft-lock is dissolved immediately.
* Target interval becomes `AVAILABLE` in OP-02.
* Expiry notification dispatched to user and fleet manager.

### State Change
`PENDING_APPROVAL` $\rightarrow$ `EXPIRED`

### Referenced Rules
* **BR-01**: Interval semantics
* **BR-02**: Exclusive Resource invariant & blocking states
* **BR-06**: Time-to-live & automatic expiration

### Main Success Scenario
1. System scheduler scans for reservations in `PENDING_APPROVAL` where $\text{currentTime} \ge \text{reviewDeadline}$ or $\text{currentTime} \ge \text{startTime}$.
2. For each identified record, system updates $\text{Reservation.state} = \text{EXPIRED}$.
3. Soft-lock is released back to inventory pool.
4. System triggers notification indicating request has expired due to timeout.

### Alternative / Failure Outcomes
* **State Race**: If an Approver submits OP-05 (Approve) or OP-06 (Reject) concurrently with the expiry sweep, atomic transaction boundaries ensure that exactly one terminal transition succeeds while the other is rejected with `INVALID_STATE_TRANSITION`.