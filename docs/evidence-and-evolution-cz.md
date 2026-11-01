# Evidence & Evolution

## Baseline a reporting identifikátory

- `C06_BASELINE_COMMIT`: `cd1f93c341afadab1c5176b5303bf100fb8eea7b`
- `CURRENT_REPORTING_COMMIT`: `cd1f93c341afadab1c5176b5303bf100fb8eea7b`

Tento snapshot repozitáře je stejný commit, který byl vyhodnocen během externího auditu C06; reporting stav níže je proto sladěn se stejným baseline a současným working tree pro korekční změny.

## Audit C06 assessment

### F1 — Confirm Reservation preconditions byly v produkčním kódu porušeny

- Claim ID & Requirement: `F1` / OP-03 Confirm Reservation, REQ-03 a Preconditions v `docs/specification/operations/03-confirm-reservation/OP-03-confirm-reservation.md`; `ReservationService.confirmReservation` musí odmítnout neaktivní vozidla a rezervace s prošlým start time.
- Reviewer Note: očekával se `RESOURCE_INACTIVE` pro neaktivní vozidla a future-start guard před `DRAFT -> CONFIRMED`/`DRAFT -> PENDING_APPROVAL`; v pozorovaném kódu se oba kontroly přeskočily.
- Our Verification: `VERIFIED FINDING`
- Disposition: `ACCEPT`
- Evidence & Resolution:
  - Porušující cesta: `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/ReservationService.java:97-120` volal `vehicleService.findByIdForUpdate(...)`, poté okamžitě zkontroloval konflikt a přepsal do `requestApproval`/`confirm` bez active-vehicle nebo future-start guard.
  - Doménová ochrana také chyběla v `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/Reservation.java:64-80` před state mutation.
  - Fix: přidány guard checks v `confirmReservation` a v `Reservation.confirm(...)` / `Reservation.requestApproval(...)` pro odmítnutí `RESOURCE_INACTIVE` a `START_MUST_BE_FUTURE` před přechodem stavu.
  - Soubory upraveny: `ReservationService.java`, `Reservation.java`, `ReservationServiceTest.java`.
  - Verification command: `./mvnw.cmd -q -Dtest=ReservationServiceTest test`
  - Výsledek: exit code `0`; suite prošla, včetně nových testů `rejectsConfirmationForInactiveVehicle` a `rejectsConfirmationWhenStartHasAlreadyPassed`.

### F2 — Design drift byl skutečný a byl opraven sladěním ReservationService s architekturou H2/H1

- Claim ID & Requirement: `F2` / ownership split architektury v `docs/specification/C04/part-2.md` a schváleného ADR.
- Reviewer Note: design diagram H2 a sequence diagram H1 explicitně vyžadují `ReservationService --> ReservationRules` a branch `ConflictOverlapException` během zpracování kolizí.
- Our Verification: `VERIFIED FINDING -> ACCEPT`
- Disposition: `ACCEPT`
- Evidence & Resolution:
  - Baseline drift skutečně existoval: `ReservationService` duplikoval privátní validaci a expiraci místo delegace do `ReservationRules`, a zpracování konfliktu házelo generický `IllegalArgumentException("CONFLICT_OVERLAP")` místo `ConflictOverlapException`.
  - To vytvořilo skutečné mismatch mezi designem a implementací ve srovnání s přijatou architekturou v `docs/specification/C04/part-2.md` a zároveň se odchýlilo od doménových pravidel v `ReservationRules` (`MIN_LEAD_TIME = 2 hours`, `calculateReviewDeadline`, `canCancel`, `shouldExpire`).
  - Fix: refaktorováno `ReservationService` tak, aby delegoval validaci okna, kontrolu zrušení a expiraci na `ReservationRules`, a upraven `ensureNoBlockingConflict(...)` tak, aby házel `ConflictOverlapException`.
  - Soubory upraveny: `src/main/java/cz/vsb/autarepo/autorepo/feature/reservation/ReservationService.java`, `src/test/java/cz/vsb/autarepo/autorepo/feature/reservation/ReservationServiceTest.java`.
  - Verification command: `./mvnw.cmd test`
  - Výsledek: exit code `0`; prošla celá Java test suite, včetně opraveného overlap assertion a layoutu lifecycle checks.

### F3 — Chybějící evidence a release markery byly skutečnou dokumentační mezerou

- Claim ID & Requirement: `F3` / C02, C03 a C05 evidence obligations ve formuláři a `docs/evidence-and-evolution.md`.
- Reviewer Note: očekával se explicitní evidence section a přesné commit/tag záznamy pro C02/C03/C05.
- Our Verification: `VERIFIED FINDING`
- Disposition: `ACCEPT`
- Evidence & Resolution:
  - Původní soubor `docs/evidence-and-evolution.md` obsahoval pouze C01 spike a částečné poznámky k C05; nebyly zde žádné sekce pro C02/C03 a žádný explicitní baseline/reporting commit record.
  - `docs/specification/C04/part-2.md` stále obsahoval placeholder strings na `...:417` a `...:455`.
  - Fix: doplněny baseline/reporting identifiers, přidán audit C06 a nahrazeny placeholder commit/tag strings ověřenou hodnotou `cd1f93c341afadab1c5176b5303bf100fb8eea7b` v `docs/specification/C04/part-2.md`.

### Supplementary PARTLY položky recenzované a dispositioned

- `B1` — baseline identification issue: `ACCEPT` jako dokumentační mezera (vyřešena tímto souborem a aktualizací C04 commit/tagu).
- `R4` / `D2` / `D4` — mismatch confirm preconditions: `ACCEPT` přes `F1`.
- `R5` — chybějící evidence pro inactive vehicle/time guard: `ACCEPT` přes `F1` + `F3`.
- `A2` / `A3` — architektonický mismatch na `ReservationRules`/`ConflictOverlapException`: `ACCEPT` přes `F2`.

## Risk assessment

Používat konzistentní ID napříč projektovými dokumenty: `R-01`..`R-05`.

### R-01
- **Risk statement:** Dva uživatelé mohou najednou potvrdit stejné vozidlo a vytvořit race, která poruší BR-02 a vytvoří překrývající se hard nebo soft lock.
- **Category:** Technical / Quality
- **Initial risk (L/I/priority/rationale):** `L=2, I=3, priority=6`, protože exkluzivní invariant vozidla je pro projekt klíčový a současná implementace ještě nebyla prokázána pod PostgreSQL contention.
- **Response strategy and rationale:** `MITIGATE`, protože lifecycle rozhodnutí patří do služby a repository conflict query je navržena tak, aby blokovala konflikty, ale skutečný důkaz konkurence ještě musí být vykonán při simultánním zatížení.
- **Treatment, owner, due:** Vynutit a otestovat transaction-level locking kolem vehicle lookup a conflict check před CP2. Owner: `AutoRepo team lead`. Due: `2026-11-15`.
- **Treatment evidence and verification status:** Proti stejnému vozidlu zatím neproběhl žádný test contention při paralelních confirm requestech. Status: `NOT VERIFIED`.
- **Expected residual risk:** `L=2, I=2, priority=4` po úspěšném concurrency testu a potvrzení lock discipline.
- **Actual residual risk / NOT VERIFIED:** `NOT VERIFIED`, protože paralelní PostgreSQL verification ještě nebyla vykonána.
- **Residual risk acceptance, owner, rationale:** Akceptovat pouze s explicitním CP2 sign-off; owner: `AutoRepo team lead`. Rationale: toto zůstává hlavní nevyřešené technické riziko pro invariant exkluzivního zdroje.
- **Monitoring, trigger, contingency, next review:** Monitorovat všechny confirm flows pro paralelní žádosti na stejné vozidlo; trigger: duplicitní confirm pokusy na jednom voze; contingency: přísnější transaction-level locking a serializable safety check; next review: `2026-11-15`.

### R-02
- **Risk statement:** Reservace v pending approval mohou vypršet příliš brzy nebo zůstat soft-locked příliš dlouho, pokud se driftne scheduler timing nebo review-deadline logika.
- **Category:** Technical / Operations
- **Initial risk (L/I/priority/rationale):** `L=2, I=2, priority=4`, protože zpracování stale approval ovlivňuje dostupnost inventáře a timeout boundary je citlivá na scheduler cadence a konzistenci času.
- **Response strategy and rationale:** `MITIGATE`, protože logika expiration approval už v servisu existuje, ale scheduler-driven behavior ještě potřebuje deterministický runtime proof před releasem.
- **Treatment, owner, due:** Ověřit deadline logiku clock-driven testy a potvrdit scheduled expiration path před CP2. Owner: `Reservation domain owner`. Due: `2026-11-15`.
- **Treatment evidence and verification status:** Unit-level expiry path je pokryt, ale skutečné scheduler-triggered vykonání zůstává neprokázané. Status: `NOT VERIFIED` pro runtime execution.
- **Expected residual risk:** `L=1, I=2, priority=2` po ověření deadline a scheduler behavior v testech.
- **Actual residual risk / NOT VERIFIED:** `NOT VERIFIED`, protože runtime expiration sweep nebyl prokázán v běhu.
- **Residual risk acceptance, owner, rationale:** Akceptovat pouze s explicitním sign-off; owner: `Reservation domain owner`. Rationale: zpoždění ovlivňuje provozní správnost, ale omezuje se jen na flow expirování schválení, nikoli na celý lifecycle rezervace.
- **Monitoring, trigger, contingency, next review:** Monitorovat pending approval stavy a deadline přechody; trigger: pending reservation zůstává neodpuštěna nebo expiruje neočekávaně; contingency: manuální expiration sweep a alerting; next review: `2026-11-15`.

### R-03
- **Risk statement:** Projekt může záviset na lokálním prostředí jednoho přispěvatele a tím pádem selhávat při reprodukci buildu nebo demo na čistém stroji.
- **Category:** People / Release / Operations
- **Initial risk (L/I/priority/rationale):** `L=2, I=3, priority=6`, protože závislost na lokálním setupu může zdržet demo a release validaci i tehdy, když je kód sám o sobě přijatelný.
- **Response strategy and rationale:** `MITIGATE`, protože repository může dokumentovat clean setup a tým jej může ověřit na druhém stroji, aby se snížila závislost na jednom člověku.
- **Treatment, owner, due:** Zaznamenat clean-checkout build/test procedure a ověřit ji na druhém stroji před CP2. Owner: `Project lead`. Due: `2026-11-12`.
- **Treatment evidence and verification status:** Dosud neexistuje záznam o ověření na druhém stroji. Status: `NOT VERIFIED`.
- **Expected residual risk:** `L=1, I=2, priority=2` po validaci setupu na druhém prostředí.
- **Actual residual risk / NOT VERIFIED:** `NOT VERIFIED`, protože clean local setup nebyl nezávisle reprodukován.
- **Residual risk acceptance, owner, rationale:** Akceptovat pouze s zaznamenaným onboarding evidence; owner: `Project lead`. Rationale: toto riziko je provozně zvládnutelné a mělo by být odstraněno před sign-offem, ne akceptováno trvale.
- **Monitoring, trigger, contingency, next review:** Monitorovat schopnost reprodukovat build na čistém prostředí; trigger: setup selže mimo lokální stroj; contingency: použití připraveného baseline prostředí; next review: `2026-11-12`.

### R-04
- **Risk statement:** Projekt se může rozšířit mimo dohodnutý CP1 boundary rozšiřováním work na approval-policy před tím, než je úplně ověřen hlavní lifecycle.
- **Category:** Scope / Requirements
- **Initial risk (L/I/priority/rationale):** `L=1, I=3, priority=3`, protože položka není zrovna pravděpodobná, ale pokud se přidají nové approval behavior před tím, než je invariant prokázán, výrazně ovlivní harmonogram.
- **Response strategy and rationale:** `ACCEPT`, protože tým může udržet aktuální rozsah soustředěný na confirmation, approval, rejection a expiration a odložit širší workflow změny do CP2.
- **Treatment, owner, due:** Udržet CP1 boundary pevné a odmítat scope expansion, pokud není vyžadován pro release readiness. Owner: `Product owner`. Due: `2026-11-22`.
- **Treatment evidence and verification status:** Repository zůstává v dohodnutém CP1 boundary. Status: `PARTLY VERIFIED`, ale nejedná se o formální gating proof.
- **Expected residual risk:** `L=1, I=2, priority=2`, pokud zůstane scope omezen.
- **Actual residual risk / NOT VERIFIED:** `NOT VERIFIED`, protože projekt formálně nesleduje scope gate mimo aktuální codebase.
- **Residual risk acceptance, owner, rationale:** Akceptovat s explicitní kontrolou scope; owner: `Product owner`. Rationale: jedná se o nízkou pravděpodobnost riziko, které je přijatelné, pokud tým drží CP1 boundary.
- **Monitoring, trigger, contingency, next review:** Monitorovat feature requests mimo flow confirmation/approval/expiration; trigger: objeví se nová approval policy; contingency: posunout změnu do CP2; next review: `2026-11-22`.

### R-05 — Timezone a clock disparity mezi hostem, JVM runtime a PostgreSQL

- **Cause -> Uncertain Event -> Consequence**: Neshoda hodin hostitelského systému / Docker containeru nebo odchylka časových pásem (např. Europe/Prague CET/CEST vs. UTC database session) -> timestamps v interval checks a precondition guards jsou konvertovány s neočekávanými offsety -> validní budoucí rezervace jsou chybně odmítány s `START_MUST_BE_FUTURE` nebo pending approval rezervace expirové příliš brzy.
- **Category**: Technical / Quality & Release / Operation
- **Initial Likelihood / Impact / Priority**: `2 / 3 / 6` (HIGH).
  - *Rationale*: Spuštění lokální JVM vedle containerizovaného PostgreSQL často přináší timezone drift; přímý dopad na hlavní invariant BR-02 a spolehlivost demo.
- **Strategy**: `MITIGATE`.
- **Treatment action**: Jednotně vynucovat UTC napříč stackem (nastavit `-Duser.timezone=UTC` / `TimeZone.setDefault("UTC")` v application bootstrap, připnout PostgreSQL JDBC URL s `?serverTimezone=UTC`) a injectovat deterministický `Clock` bean do všech služeb a scheduler tasků místo volání `LocalDateTime.now()` bez ukotvení.
- **Owner**: Reservation domain owner
- **Due date**: `2026-11-15`
- **Effectiveness evidence / status**: Ověřené timezone settings v `application.properties` a integration test ověřující UTC timestamp persistence přes DB round-trip.
- **Expected Residual**: `1 x 1 = 1` (LOW).
- **Actual Residual**: `NOT VERIFIED` (podle pravidel rubriky zůstává neověřeno, dokud neprojde end-to-end integration test proti PostgreSQL containeru).
- **Acceptance & Trigger**: Team lead akceptuje s UTC policy; trigger: jakékoli neočekávané odmítnutí `START_MUST_BE_FUTURE` u budoucích rezervací nebo test fail pod non-UTC JVM timezone.

## Evidence summary

- Ověřená oprava F1: `Resource inactive` a `start time already reached` se nyní odmítají před přechodem stavu.
- Přijaté a opravené F2: `ReservationService` nyní deleguje vyhodnocování pravidel do `ReservationRules` a při překryvu hází `ConflictOverlapException`, což odpovídá designu H2/H1.
- Dokumentační mezera F3: uzavřena přidáním evidence auditu a baseline commit markerů.
- Automatická verifikace: `./mvnw.cmd test` skončila úspěšně s kódem `0`.
