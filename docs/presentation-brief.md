# Presentation Brief — AutoRepo C08

## Presentation goal

The presentation should show that the core reservation lifecycle is
implemented, the C06 corrections are closed, and the tests pass with PostgreSQL
running in Docker. The status remains `AMBER` because not everything that is
implemented has been independently proven under concurrency and runtime
conditions.

Recommended duration: **8–10 minutes**. Do not read every word from the
slides; explain one main idea and one piece of evidence for each slide.

## Opening — approximately 30 seconds

> “I am presenting the current C08 status of the AutoRepo project. I will
> focus on the reservation lifecycle, the corrections from the previous
> assessment, and what has been verified by tests. The current status is
> AMBER: the core works and the full test suite passes, but dedicated evidence
> is still missing for concurrency, scheduler expiration, and reproducibility
> on a second environment.”

## Slide 1 — Executive Project Status

**Main point:** the project is functionally healthy, but it cannot yet be
claimed as `GREEN`.

Say:

> “Findings F1 to F3 from C06 have been addressed. After starting Docker,
> PostgreSQL is healthy and `mvnw.cmd -q test` completed successfully. The
> status remains AMBER because the full suite verifies the normal integration
> path, not specifically two simultaneous confirmation attempts or the full
> runtime behavior of scheduler expiration.”

Emphasize the difference:

- **Implemented:** the code and normal integration scenarios exist.
- **Verified:** a specific test or reproducible piece of evidence confirms the
  behavior.

## Slide 2 — Scope & Delivery Evidence

**Main point:** the main capabilities have specifications, design,
implementation, and basic tests; the BR-02 invariant still lacks dedicated
concurrency evidence.

Say:

> “Create, availability, confirm/approval, and cancel are covered by code and
> tests. BR-02 has an implemented overlap check and the PostgreSQL integration
> works, but we do not yet have a dedicated test that starts two confirmation
> requests at the same time for the same vehicle. Therefore this item is
> PARTLY, not YES.”

If asked about PostgreSQL:

> “The previous failure was not caused by Hibernate or business logic. The
> container was not running. After Docker was started, the persistence test
> connected to PostgreSQL 16 and the full Maven suite passed.”

## Slide 3 — Schedule, Resources & Dependencies

**Main point:** the C06 corrections are complete; the next step is evidence,
not a large new feature.

Say:

> “With the environment started correctly, database availability is no longer
> the main dependency. The three critical remaining controls are parallel
> confirmation, scheduler expiration, and verification on a second clean
> environment.”

Do not claim that concurrency or the scheduler are fully verified merely
because the corresponding methods are implemented. Describe them as open
verification tasks.

## Slide 4 — Top Risks

**Main point:** these risks are unproven scenarios, not confirmed defects.

- `R-01`: simultaneous confirmation transactions for the same vehicle.
- `R-02`: the scheduler may run late or may not have been verified at the
  deadline boundary.
- `R-03`: the build may depend on the author's local environment.

Say:

> “I do not treat a planned control as a reduction of the actual risk. Until
> the evidence is executed, the actual residual status is NOT VERIFIED.”

## Slide 5 — Next Action Plan

**Main point:** every open risk has an owner, a deadline, and a measurable
result.

Say:

> “P1 must prove that parallel confirmations do not violate BR-02. P2 must
> prove the deadline boundary, the transition to EXPIRED, and release of the
> soft-lock. P3 must remove the dependency on a single local environment.
> Only then can AMBER be reassessed.”

## Slide 6 — Management Conclusion

**Main point:** the decision is conditional continuation, not a declaration
that all risks are closed.

Closing statement:

> “The core reservation lifecycle is implemented, findings F1 to F3 are
> closed, and the full test suite passes with PostgreSQL running in Docker. I
> recommend continuing toward CP2 conditionally: before final sign-off, we
> should add the concurrency test, the scheduler runtime evidence, and a
> second-environment reproduction. Therefore the final status is AMBER.”

## Technical line for questions

If there is time for detail, explain the `OP-03 Confirm Reservation` path:

```text
ReservationViewController
  -> ReservationService.confirmReservation(id)
  -> load reservation and require DRAFT
  -> VehicleService.findByIdForUpdate(...)
  -> check active vehicle and future start
  -> ReservationRepository.hasStateConflict(...)
  -> confirm(...) or requestApproval(...)
  -> saveAndFlush(...)
  -> notification
```

The key rule is `BR-02`: only `CONFIRMED` and `PENDING_APPROVAL` block a
vehicle, and intervals use half-open semantics `[start, end)`. A reservation
starting exactly at the previous reservation's `end` time is therefore not a
conflict.

## Most likely questions and short answers

**Why is the status AMBER if all tests passed?**  
The successful suite proves the executed scenarios, but there is no dedicated
stress test for simultaneous PostgreSQL transactions, no runtime evidence for
scheduler expiration, and no second clean environment.

**What exactly did Docker solve?**  
It made the PostgreSQL container available. Without it, Hibernate could not
obtain JDBC metadata and determine the dialect. After the container started,
the database tests connected and the suite passed.

**Why is `PENDING_APPROVAL` a blocking state?**  
It is a soft-lock. It prevents another reservation from taking the same
interval while approval is pending.

**What was F1?**  
Confirmation previously did not check whether the vehicle was active or
whether the reservation start had already passed. It now rejects both an
inactive vehicle and a reservation whose start time has been reached.

**What was F2?**  
It was a design-to-implementation drift: the rules were not delegated through
`ReservationRules`, and conflicts did not use `ConflictOverlapException`.
Both parts were aligned.

**What was F3?**  
Consistent evidence and baseline/reporting identifiers were missing. The
documentation update closed that gap.

**Does `FOR UPDATE` prove that concurrency is solved?**  
Not by itself. It is an important part of the access-serialization design,
but final evidence requires a parallel PostgreSQL test with an observed
result.

## Files to have ready before presenting

- `docs/SWI_C08_Project_Status_FILLED2.pptx`
- `docs/technical-defense-brief.md`
- `src/main/java/.../ReservationService.java`
- `src/main/java/.../Reservation.java`
- `src/main/java/.../common/domain/ReservationRules.java`
- `src/test/java/.../ReservationServiceTest.java`
- `src/test/java/.../ReservationPersistenceSpikeTest.java`

Before demonstrating the environment, run:

```powershell
docker compose ps
.\mvnw.cmd -q test
```

Answer from the evidence. If something has not been independently tested, say
“implemented, but not yet independently verified” instead of claiming that it
is fully proven.
