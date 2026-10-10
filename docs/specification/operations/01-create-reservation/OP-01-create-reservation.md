# OP-01 — Create Reservation

### Goal / User Value
User creates a reservation draft (putting a selected car in the basket) to record their booking intent without prematurely locking the car against other browsing users.

---

### Trigger
User submits a draft booking request for Resource (Car) $R$ and interval $I = [\text{start}, \text{end})$.

---

### Observable Requirement
- **REQ-01**: The system shall create a `DRAFT` Reservation for an existing Resource when the requested interval satisfies $\text{start} < \text{end}$ and starts in the future ($\text{start} > \text{currentTime}$).

---

### Preconditions
- Target Resource (Car) exists.
- Requested interval is valid ($\text{start} < \text{end}$ per BR-01).
- Interval starts in the future ($\text{start} > \text{currentTime}$ per BR-Domain).

---

### Success Postconditions
- Exactly one new Reservation entity exists in the system.
- `Reservation.state` = `DRAFT`.
- No car allocation or soft-lock is committed (BR-04 applies; the car remains available to others).
- The unique Reservation ID, car ID, interval, and state `DRAFT` are returned to the user.

---

### State Change
`[none]` $\rightarrow$ `DRAFT`

---

### Referenced Rules
- **BR-01**: Interval semantics
- **BR-04**: Draft intent & non-allocation
- **BR-Domain**: Lead time & maximum duration

---

### Main Success Scenario
1. User provides `resource_id` (Car) and interval $[\text{start}, \text{end})$.
2. System validates that the Resource exists.
3. System validates interval syntax ($\text{start} < \text{end}$) and non-past constraint ($\text{start} > \text{currentTime}$).
4. System creates Reservation with state set to `DRAFT`.
5. System returns the Reservation identifier, car identifier, interval, and state `DRAFT`.

---

### Alternative / Failure Outcomes
- **Unknown Resource**: Car does not exist $\rightarrow$ rejected with `RESOURCE_NOT_FOUND`; no Reservation created.
- **Invalid Interval ($\text{start} \ge \text{end}$)**: Bounds inverted or equal $\rightarrow$ rejected with `INVALID_INTERVAL`; no Reservation created.
- **Interval in Past ($\text{start} \le \text{currentTime}$)**: Booking starts in past or present $\rightarrow$ rejected with `INTERVAL_IN_PAST`; no Reservation created.

---

### Verification Examples
- **Positive**: Car `CAR-01` exists, interval $[\text{tomorrow 10:00}, \text{tomorrow 14:00})$ $\rightarrow$ Reservation created with state `DRAFT`.
- **Negative (Zero duration)**: $[10:00, 10:00)$ $\rightarrow$ rejected with `INVALID_INTERVAL`.
- **Negative (Inverted interval)**: $[14:00, 10:00)$ $\rightarrow$ rejected with `INVALID_INTERVAL`.
- **Negative (Car does not exist)**: Query for car `CAR-999` $\rightarrow$ rejected with `RESOURCE_NOT_FOUND`.
- **Edge (Coexisting Drafts)**: User A creates draft for `CAR-01` at $[10:00, 14:00)$; User B simultaneously creates draft for `CAR-01` at $[11:00, 15:00)$ $\rightarrow$ both succeed with state `DRAFT` (`DRAFT` does not lock inventory).

---

### Rationale
Matches basket creation in the booking journey. Intent is recorded without creating artificial denial-of-inventory for other users.