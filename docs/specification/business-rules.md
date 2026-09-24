# Domain Rules and Invariants (Baseline v0.1)

This document serves as the single source of truth for all domain rules, temporal semantics, and system invariants. Individual operations (OP-01 through OP-04) reference these definitions to avoid redundancy and semantic drift.

---

## BR-01 — Interval Semantics
All reservation and availability intervals in the system adhere strictly to half-open interval semantics: `[start, end)`.

- **Coverage:** A reservation scheduled for `[10:00, 12:00)` occupies the Resource from `10:00:00.000` up through `11:59:59.999`.
- **Adjacency:** An adjacent reservation starting at `12:00:00.000` (e.g., `[12:00, 14:00)`) does **not** overlap with `[10:00, 12:00)`. Seamless handover at the exact boundary point is valid.
- **Syntactic Validity:** An interval is valid if and only if `start < end`.
    - Zero-duration intervals (`start == end`) are strictly invalid.
    - Inverted intervals (`start > end`) are strictly invalid.

---

## BR-02 — Exclusive Resource Invariant
At no committed system state may two `CONFIRMED` reservations overlap for the exact same exclusive Resource (Car).

- **Overlap Definition:** An overlap between interval $A = [A.start, A.end)$ and interval $B = [B.start, B.end)$ occurs if and only if:
  $$\max(A.start, B.start) < \min(A.end, B.end)$$
- **Scope of Invariant:**
    - Only reservations with `state == CONFIRMED` represent a committed allocation of the vehicle.
    - Reservations in `DRAFT` or `CANCELLED` do not hold or allocate inventory and cannot violate this invariant.
- **Enforcement:** Enforced strictly at the confirmation boundary (`OP-03`). If an overlap with an existing `CONFIRMED` reservation would occur, the operation must fail.

---

## BR-03 — Cancellation Policy
A reservation may be voluntarily withdrawn if and only if all following conditions are satisfied:

1. **Eligible State:** `Reservation.state ∈ {DRAFT, CONFIRMED}`.
2. **Temporal Window:** The current system time must be strictly earlier than the reservation start time:
   $$\text{currentTime} < \text{Reservation.start}$$
3. **Effect on Inventory:** Upon successful transition to `CANCELLED`, any previous claim on the Resource is immediately released. `OP-02` will immediately reflect the Resource as `AVAILABLE` for that interval.
4. **Idempotence & Re-cancellation:** Submitting cancellation for a reservation already in state `CANCELLED` is explicitly rejected with `ALREADY_CANCELLED`.
5. **Boundary Cutoff:** If $\text{currentTime} \ge \text{Reservation.start}$, cancellation is rejected with `CANCELLATION_WINDOW_CLOSED`.

---

## BR-04 — Draft Intent & Non-Allocation
1. **Purpose:** Creation of a reservation (`OP-01`) records user booking intent (placing a vehicle in the basket) without committing an allocation.
2. **No Inventory Claim:** A `DRAFT` reservation does not lock the car and has zero blocking effect in availability checks (`OP-02`).
3. **Coexistence:** Multiple users may simultaneously hold overlapping `DRAFT` reservations for the same vehicle.
4. **Resolution Point:** Competition between overlapping drafts is resolved on a first-to-confirm basis during `OP-03 Confirm Reservation`.

---

## BR-05 — Resource Exclusivity Assumption
- Every Resource (Car) is treated as a unique, non-fungible, exclusive entity with a capacity of 1.
- Capacity-based or shared allocations are out of scope for Baseline v0.1.