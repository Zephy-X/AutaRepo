# System Operations Summary (OP-01 – OP-07)

| Op ID | Operation Name | Primary Actor | Trigger Event | Allowed Input State(s) | Resulting Output State | Key Side Effect on Inventory |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **OP-01** | Create Reservation | User | Adds car interval to basket | *None* (New Record) | `DRAFT` | No lock; records booking intent only. |
| **OP-02** | Check Availability | User / Approver | Browses cars or queries date range | Any (Read-Only) | *N/A* (Query response) | Reads inventory: treats `CONFIRMED` and `PENDING_APPROVAL` as unavailable. |
| **OP-03** | Confirm Reservation | User | Submits booking | `DRAFT` | Standard: `CONFIRMED`<br><br>Luxury: `PENDING_APPROVAL` | Standard: Hard-lock.<br><br>Luxury: Soft-lock (exclusive hold). |
| **OP-04** | Cancel Reservation | User / Approver | User or manager cancels before start | `DRAFT`, `PENDING_APPROVAL`, `CONFIRMED` | `CANCELLED` | Releases lock immediately; returns interval to public pool. |
| **OP-05** | Approve Reservation | Approver | Manager accepts luxury booking request | `PENDING_APPROVAL` | `CONFIRMED` | Converts existing soft-lock into permanent hard allocation. |
| **OP-06** | Reject Reservation | Approver | Manager declines luxury booking request | `PENDING_APPROVAL` | `REJECTED` | Releases soft-lock immediately; slot becomes available. |
| **OP-07** | Expire Pending Reservation | System (Scheduler) | $\text{now} \ge \text{reviewDeadline} \lor \text{now} \ge \text{start}$ | `PENDING_APPROVAL` | `EXPIRED` | Automatically dissolves soft-lock; frees up inventory. |