
## 6. SWI C03 Part A — AS-IS Scenario Mapping (Confirm Reservation)

### A1. Scenario Reference

| Item | Value |
|---|---|
| Scenario / operation | OP-03 Confirm Reservation |
| Requirements | OP-03, OP-02 |
| Rules / invariants | BR-02 (exclusive overlap), BR-05 (requires approval route), BR-01 (half-open overlap query semantics) |
| Baseline | v0.2 |

### A2. Main scenario path mapped to code

| C02 scenario step | Implementation location | Evidence |
|---|---|---|
| accept confirmation request | `ReservationViewController.confirm(Long, RedirectAttributes)` | `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/ReservationViewController.java:119-125` |
| load Reservation | `ReservationService.confirmReservation(Long)` calls `findById(id)` | `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/ReservationService.java:79-81,49-52` |
| check transition allowed (`DRAFT` only) | `if (reservation.getState() != ReservationState.DRAFT) throw ...` | `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/ReservationService.java:81-83` |
| lock/load Vehicle for command | `VehicleService.findByIdForUpdate(...)` then attach to reservation | `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/ReservationService.java:85-87`, `src/main/java/cz/vsb/autarepo/autorepo/feature/vehicle/VehicleService.java:30-34` |
| evaluate conflict vs blocking states | `ensureNoBlockingConflict(...)` -> `reservationRepository.hasStateConflict(...)` (`CONFIRMED`, `PENDING_APPROVAL`, overlap predicate) | `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/ReservationService.java:87,193-196`, `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/ReservationRepository.java:41-54` |
| choose target state based on policy | `vehicle.isRequiresApproval()` branch | `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/ReservationService.java:90-97` |
| change state (standard vehicle path) | `reservation.confirm(now)` sets `CONFIRMED` | `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/ReservationService.java:98`, `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/Reservation.java:68-73` |
| persist result | `reservationRepository.save(...)` + `flush()` | `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/ReservationService.java:99-100` |
| notify side effect | `notificationService.notifyReservationConfirmed(saved)` | `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/ReservationService.java:101`, `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/NotificationServiceStub.java:11-13` |

### A3. One important alternative/failure path

Selected branch: **conflict exists -> confirmation is rejected**.

| v0.2 behaviour | Where condition is detected | Where outcome is decided | What caller receives |
|---|---|---|---|
| conflict -> reject | `reservationRepository.hasStateConflict(...)` query over `CONFIRMED` + `PENDING_APPROVAL` overlapping intervals | `ReservationService.ensureNoBlockingConflict(...)` throws `ConflictOverlapException` | MVC action catches it and redirects with domain error message |

Evidence:
- Condition query: `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/ReservationRepository.java:41-54`
- Decision/throw: `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/ReservationService.java:193-196`
- Caller outcome mapping: `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/ReservationViewController.java:154-158`
- Integration behaviour: `src/test/java/cz/vsb/autarepo/autorepo/feature/reservation/ReservationLifecycleIntegrationTest.java:81-83`

v0.2 ↔ implementation mismatch:

| Specification | Implementation | Evidence |
|---|---|---|
| Confirm Reservation directly commits allocation (`DRAFT` -> `CONFIRMED`) | If `vehicle.requiresApproval == true`, `confirmReservation` transitions `DRAFT` -> `PENDING_APPROVAL` and emits pending-approval notification | `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/ReservationService.java:90-95`, `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/Reservation.java:75-81` |

### A4. Main implementation elements (scenario-realizing)

| Implementation element | Type / contents | Role in this scenario | Evidence |
|---|---|---|---|
| Reservation API | `ReservationViewController` | accepts confirm command and maps exception outcome to user-facing flash message | `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/ReservationViewController.java:119-125,147-159` |
| Reservation application logic | `ReservationService` | orchestrates load, state eligibility, conflict decision, branch-to-confirm-or-pending, persistence, notification | `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/ReservationService.java:79-103` |
| Reservation aggregate state logic | `Reservation` entity methods (`confirm`, `requestApproval`) | executes lifecycle transition and writes transition fields (`state`, `approvedAt`, `submittedAt`, `reviewDeadline`) | `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/Reservation.java:68-81` |
| Persistence boundary | `ReservationRepository` | stores reservation state and evaluates overlap conflict with blocking states | `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/ReservationRepository.java:41-58` |
| Vehicle lock boundary | `VehicleService.findByIdForUpdate` (+ repository call) | obtains row-locked vehicle read used during confirmation | `src/main/java/cz/vsb/autarepo/autorepo/feature/vehicle/VehicleService.java:30-34` |
| Notification integration | `NotificationServiceStub` | emits scenario-completion event (`confirmed` or `pending approval`) | `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/NotificationServiceStub.java:11-17,31-37` |

### A5. State, state change ownership, and one rule

State

| Question | Answer | Evidence |
|---|---|---|
| Where is Reservation state persisted? | PostgreSQL table mapped by JPA entity `Reservation` (`@Entity`, `@Table(name = "reservations")`), persisted via `ReservationRepository` | `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/Reservation.java:15-19`, `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/ReservationRepository.java:16` |
| Which code decides/executes the transition used in the scenario? | Decision/orchestration in `ReservationService.confirmReservation`; transition execution in `Reservation.confirm` or `Reservation.requestApproval`; persistence in `reservationRepository.save(...)+flush()` | `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/ReservationService.java:79-103`, `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/Reservation.java:68-81` |

Business rule / invariant selected: **BR-02 exclusive overlap (no blocking overlap for same vehicle)**.

| Question | Answer | Evidence |
|---|---|---|
| Where is the rule condition detected? | In repository query `hasStateConflict(...)` using overlap predicate (`start < endTime` and `end > startTime`) and blocking states (`CONFIRMED`, `PENDING_APPROVAL`) | `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/ReservationRepository.java:41-48` |
| Where is the outcome decided? | In service guard `ensureNoBlockingConflict(...)` that throws `ConflictOverlapException` when condition is true | `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/ReservationService.java:193-196` |
| Where is resulting state change performed? | If no conflict: `Reservation.confirm(now)` or `Reservation.requestApproval(now)` executes transition; then save/flush persists it | `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/ReservationService.java:91-100`, `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/Reservation.java:68-81` |

### A6. Relevant dependencies

| Dependency | Where it connects to your code | Which part knows its technical API | Evidence |
|---|---|---|---|
| PostgreSQL (reservations) | JPA repository operations during confirm (`findById`, conflict query, `save`, `flush`) | `ReservationRepository` and JPA/Hibernate-managed entity mapping | `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/ReservationService.java:80,87,99-100`, `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/ReservationRepository.java:41-54` |
| PostgreSQL (vehicle row lock) | confirmation flow calls `VehicleService.findByIdForUpdate` before conflict check | vehicle persistence boundary (`VehicleService` + `VehicleRepository` lock method) | `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/ReservationService.java:85`, `src/main/java/cz/vsb/autarepo/autorepo/feature/vehicle/VehicleService.java:30-34` |
| Notification service (currently stubbed) | `notifyReservationConfirmed` / `notifyReservationPendingApproval` after successful confirm path | `NotificationServiceStub` (`logger.info` stub API) | `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/ReservationService.java:94,101`, `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/NotificationServiceStub.java:11-17,31-37` |

### A7. AS-IS structural diagram (current realization of OP-03)

```text
+-------------------------- Application code ---------------------------+
|                                                                       |
| [Reservation API] (class)                                             |
| role: accepts confirm command and returns redirect outcome            |
| ReservationViewController                                              |
|        | confirm(id)                                                   |
|        v                                                               |
| [Reservation logic] (class)                                           |
| role: orchestrates confirm flow & conflict decision                   |
| ReservationService                                                     |
|   | findById/save/flush                 | findByIdForUpdate            |
|   v                                     v                              |
| [Reservation persistence] (class)      [Vehicle lock access] (class)  |
| ReservationRepository                  VehicleService                  |
|   |                                                             |      |
|   | notify confirmed/pending                                    |      |
|   v                                                             |      |
| [Notification integration] (class)                              |      |
| NotificationServiceStub                                         |      |
|                                                                       |
+-----------------------------------------------------------------------+
         | read/write reservations & overlap query          | lock/read vehicle
         v                                                  v
 [PostgreSQL: reservations table]                 [PostgreSQL: vehicles table]
```

### A8. Question for next C03 architecture step

| Item | Content |
|---|---|
| Question | Can two concurrent `confirmReservation` requests for overlapping drafts on the same vehicle both pass `hasStateConflict(...)` before either transaction commits, resulting in a BR-02 violation? |
| Evidence | Conflict check (`hasStateConflict`) and write (`save/flush`) are separate operations in `ReservationService.confirmReservation`; repository conflict query itself is not `@Lock(PESSIMISTIC_WRITE)` while a different method (`hasBlockingOverlap`) is locked but unused in this flow. |
| Why it matters | BR-02 must hold under concurrent confirmations; this directly impacts transaction isolation/locking strategy in C03 architecture decisions. |

Evidence refs:
- `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/ReservationService.java:87,99-100,193-196`
- `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/ReservationRepository.java:26-39,41-54`
