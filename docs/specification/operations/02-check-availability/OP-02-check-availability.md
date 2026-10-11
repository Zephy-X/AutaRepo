# OP-02 — Check Availability

### Goal / User Value
User or Approver queries whether a specific car is available for a desired time interval before initiating or confirming a booking.

### Trigger
User or Approver queries availability for Resource (Car) $R$ and interval $I = [\text{start}, \text{end})$.

### Observable Requirement
* **REQ-02**: For a valid interval and existing Resource, the system shall report `AVAILABLE` if the interval overlaps any `CONFIRMED` or `PENDING_APPROVAL` Reservation on that Resource under BR-02; otherwise it shall report `AVAILABLE`.

### Preconditions
* Target Resource (Car) exists.
* Requested interval is valid ($\text{start} < \text{end}$ per BR-01).

### Success Postconditions
* An availability result (`AVAILABLE` or `UNAVAILABLE`) is returned.
* No system entity is created or modified (pure read-only query).

### State Change
None.

### Referenced Rules
* **BR-01**: Interval semantics
* **BR-02**: Exclusive Resource invariant & blocking states
* **BR-04**: Draft intent & non-allocation

### Main Success Scenario
1. User or Approver submits `resource_id` (Car) and interval $[\text{start}, \text{end})$.
2. System validates that the car exists.
3. System validates that $\text{start} < \text{end}$.
4. System queries all blocking reservations ($\text{state} \in \{\text{CONFIRMED}, \text{PENDING\_APPROVAL}\}$) for this car overlapping $[\text{start}, \text{end})$ per BR-01 and BR-02.
5. If at least one overlapping blocking reservation is found, system returns `UNAVAILABLE`.
6. If no overlapping blocking reservation exists, system returns `AVAILABLE`.

### Alternative / Failure Outcomes
* **Unknown Resource**: Car does not exist $\rightarrow$ rejected with `RESOURCE_NOT_FOUND`.
* **Invalid Interval (**$\text{start} \ge \text{end}$**)**: Bounds inverted or equal $\rightarrow$ rejected with `INVALID_INTERVAL`.

### Verification Examples
Given existing `CONFIRMED` booking for `CAR-01`: $[10:00, 12:00)$
* Query $[08:00, 10:00) \rightarrow$ `AVAILABLE` (adjacent boundary touches at 10:00, no overlap under half-open $[\text{start}, \text{end})$).
* Query $[10:30, 11:30) \rightarrow$ `UNAVAILABLE` (fully interior overlap).

Given existing `PENDING_APPROVAL` soft-lock for `LUX-01`: $[14:00, 18:00)$
* Query $[15:00, 17:00) \rightarrow$ `UNAVAILABLE` (`PENDING_APPROVAL` holds an exclusive soft-lock per BR-02).
* Query $[18:00, 20:00) \rightarrow$ `AVAILABLE` (adjacent boundary touches at 18:00).

Given existing `DRAFT` reservation for `CAR-01`: $[14:00, 16:00)$
* Query $[14:00, 16:00) \rightarrow$ `AVAILABLE` (`DRAFT` does not block availability per BR-04).