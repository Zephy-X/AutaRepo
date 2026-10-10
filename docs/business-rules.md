# Detailed Domain Rules & Invariants

| Rule ID | Name | Core Principle | Key Enforcement & Constraints |
| :--- | :--- | :--- | :--- |
| **BR-01** | Interval Semantics | Half-open intervals: $[start, end)$. | Clean boundaries at exact second; $start < end$ strictly required (no zero-length or inverted slots). |
| **BR-02** | Resource Invariant & Locking | No overlapping blocking states for the same car. | Hard-lock: `CONFIRMED`.<br>Soft-lock: `PENDING_APPROVAL`.<br>Both block availability and prevent double-booking. |
| **BR-03** | Cancellation Policy | Voluntary withdrawal before trip start. | Allowed only while $\text{now} < \text{start}$ from `DRAFT`, `PENDING_APPROVAL`, or `CONFIRMED`. Releases all inventory locks immediately. |
| **BR-04** | Draft Intent | Basket items hold zero inventory. | Multiple users can draft the same car simultaneously. Slot is claimed on a first-come, first-served basis at confirmation (`OP-03`). |
| **BR-05** | Luxury Vehicle Policy | High-value cars require manager oversight. | Standard cars confirm immediately; luxury cars enter `PENDING_APPROVAL` with an exclusive soft-lock until approved or rejected. |
| **BR-06** | Automatic Expiration (TTL) | Pending requests cannot hang indefinitely. | Auto-cancels to `EXPIRED` if the review SLA runs out or trip start time arrives without a decision. Releases soft-lock immediately. |
| **BR-07** | Physical Exclusivity | Every vehicle capacity = 1. | Strict 1-to-1 physical resource mapping; no pooled or fractional allocations. |


## BR-01 — Interval Semantics
All reservation and availability intervals in the system adhere strictly to half-open interval semantics: $[start, end)$.

- **Coverage:** A reservation scheduled for $[10:00, 12:00)$ occupies the Resource from 10:00:00.000 up through 11:59:59.999.
- **Adjacency:** An adjacent reservation starting at 12:00:00.000 (e.g., $[12:00, 14:00)$) does not overlap with $[10:00, 12:00)$. Seamless handover at the exact boundary point is valid.
- **Syntactic Validity:** An interval is valid if and only if $start < end$.
    - Zero-duration intervals ($start = end$) are strictly invalid.
    - Inverted intervals ($start > end$) are strictly invalid.

---

## BR-02 — Exclusive Resource Invariant & Blocking States
At no committed system state may two blocking reservations overlap for the exact same exclusive Resource (Car).

- **Overlap Definition:** An overlap between interval $A = [A.start, A.end)$ and interval $B = [B.start, B.end)$ occurs if and only if:
  $$\max(A.start, B.start) < \min(A.end, B.end)$$

- **Blocking States (Inventory Claims):**
    - `CONFIRMED` (Hard Lock): Permanent binding allocation locked to the user.
    - `PENDING_APPROVAL` (Soft Lock): Exclusive provisional hold granted while awaiting an Approver decision. Blocks availability (`OP-02`) and prevents any concurrent user from drafting or confirming an overlapping slot.

- **Non-Blocking States:**
    - Reservations in `DRAFT`, `CANCELLED`, `REJECTED`, or `EXPIRED` hold zero inventory and do not block availability.

- **Enforcement Boundary:**
    - Enforced during `OP-03 Confirm Reservation`. A reservation cannot transition to `CONFIRMED` or `PENDING_APPROVAL` if an overlapping `CONFIRMED` or `PENDING_APPROVAL` record exists.
    - Because `PENDING_APPROVAL` enforces exclusive soft-locking at confirmation submission, subsequent promotion to `CONFIRMED` via `OP-05` is guaranteed free of conflict collisions.

---

## BR-03 — Cancellation Policy
A reservation may be voluntarily cancelled by a User or Approver if and only if all following conditions are satisfied:

1. **Eligible States:** $\text{Reservation.state} \in \{\text{DRAFT}, \text{PENDING\_APPROVAL}, \text{CONFIRMED}\}$.
2. **Temporal Window:** The current system time must be strictly earlier than the reservation start time:
   $$\text{currentTime} < \text{Reservation.start}$$
3. **Effect on Inventory:** Upon transition to `CANCELLED`, all claims (soft-lock or hard-lock) are released immediately. `OP-02 Check Availability` will immediately reflect the interval as available.
4. **Terminal State & Idempotence:** Submitting cancellation for any reservation already in a terminal state (`CANCELLED`, `REJECTED`, or `EXPIRED`) is rejected with `INVALID_STATE_TRANSITION`.
5. **Boundary Cutoff:** If $\text{currentTime} \ge \text{Reservation.start}$, voluntary cancellation is rejected with `CANCELLATION_WINDOW_CLOSED`.

---

## BR-04 — Draft Intent & Non-Allocation
1. **Purpose:** Creation of a reservation (`OP-01`) records user booking intent (placing a vehicle into the cart) without committing an allocation.
2. **No Inventory Claim:** A `DRAFT` reservation does not lock the car and has zero blocking effect in availability checks (`OP-02`).
3. **Coexistence:** Multiple users may simultaneously hold overlapping `DRAFT` reservations for the exact same vehicle and interval.
4. **Resolution Point:** Competition between overlapping drafts is resolved on a first-to-confirm basis at `OP-03`. The first submission to clear conflict validation claims the vehicle (either directly to `CONFIRMED` or as a soft-lock in `PENDING_APPROVAL`). All competing drafts attempting `OP-03` thereafter are rejected with `RESOURCE_UNAVAILABLE`.

---

## BR-05 — Luxury Vehicle Policy & Authorization Lifecycle
Resources flagged with `resource.isLuxury == true` require administrative oversight before a binding commitment is finalized.

1. **Routing:** At `OP-03`, non-luxury resources transition directly to `CONFIRMED`. Luxury resources transition to `PENDING_APPROVAL`.
2. **Exclusivity During Review:** `PENDING_APPROVAL` establishes a valid soft-lock under BR-02, protecting the user from having their slot poached while awaiting review.
3. **Approver Actions:**
    - **Approval (`OP-05`):** An authorized Approver (Fleet Manager) promotes `PENDING_APPROVAL` to `CONFIRMED`, converting the soft-lock into a permanent hard allocation.
    - **Rejection (`OP-06`):** An Approver declines the request. State transitions to `REJECTED`, the soft-lock is released immediately, and the state becomes terminal.

---

## BR-06 — Time-to-Live (TTL) & Automatic Expiration
Pending luxury reservations must not hold a vehicle indefinitely if left unreviewed.

1. **Review Deadline:** Every reservation entering `PENDING_APPROVAL` is assigned an automated review deadline:
   $$\text{reviewDeadline} = \min(\text{submittedAt} + \Delta T_{\text{SLA}}, \text{Reservation.start})$$
   *(where $\Delta T_{\text{SLA}}$ is the fleet review timeout, e.g., 24 hours)*.
2. **Automatic Expiry Trigger (`OP-07`):** If no Approver decision is recorded and:
   $$\text{currentTime} \ge \text{reviewDeadline} \quad \lor \quad \text{currentTime} \ge \text{Reservation.start}$$
   the system automatically transitions the reservation to `EXPIRED`.
3. **Effect on Inventory:** Transition to `EXPIRED` dissolves the soft-lock immediately, restoring the resource interval to the open availability pool (`OP-02`).

---

## BR-07 — Resource Exclusivity Assumption
- Every Resource (Car) is treated as a unique, non-fungible, exclusive physical entity with a discrete capacity of 1.
- Capacity-based, pooled, or fractional allocations are strictly out of scope.