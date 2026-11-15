# Evidence & Evolution

## Baseline a reporting identifikátory

- `C06_BASELINE_COMMIT`: `cd1f93c341afadab1c5176b5303bf100fb8eea7b`
- `CURRENT_REPORTING_COMMIT`: `cd1f93c341afadab1c5176b5303bf100fb8eea7b`
- `C08_BASELINE_COMMIT`: `c8db481f5913753e2e86abb5791ebbb875e78963` (viz [sekce C08](#c08--before-baseline-a-testovací-evidence))

Tento snapshot repozitáře je stejný commit, který byl vyhodnocen během externího auditu C06; reporting stav níže je proto sladěn se stejným baseline a současným working tree pro korekční změny.

## Index evidence C02/C03/C05

GitHub Issues dokazují historii trackování práce, ale uzavřený issue sám o sobě nedokazuje, že byly vytvořeny všechny povinné výstupy. Tabulka níže odděluje repository artefakty, tracking záznamy, implementační commity a ověřovací důkazy. Pro tyto deliverables nebyl nalezen repository tag; proto jsou použity přesné odkazy na commity a pull requesty.

| Kurz / deliverable | Požadavek nebo výstup | Typ evidence | Přesná evidence | Status | Poznámky / zbývající mezera |
|---|---|---|---|---|---|
| C02 | Baseline v0.2 approval workflow, dopad na slices, pravidla a verification examples | Repository dokument; issue; PR; commit | [`docs/baseline-v0.2-change-record.md`](baseline-v0.2-change-record.md); [Issue #6](https://github.com/Zephy-X/AutaRepo/issues/6); [PR #7](https://github.com/Zephy-X/AutaRepo/pull/7); [commit `70f0d33`](https://github.com/Zephy-X/AutaRepo/commit/70f0d33d20c7fd231d27b79fd8e175247d3c9049) | VERIFIED | Baseline record obsahuje změnový dopad a provedené verification examples. Samostatný repository tag nebyl nalezen. |
| C03 Part A | AS-IS mapování scénáře, implementační prvky, stav/pravidla, závislosti a strukturální evidence | Repository adresář; issue; PR; commit | [`docs/specification/C03/`](specification/C03/); [Issue #8](https://github.com/Zephy-X/AutaRepo/issues/8); [PR #9](https://github.com/Zephy-X/AutaRepo/pull/9); [commit `de27a59`](https://github.com/Zephy-X/AutaRepo/commit/de27a59c6395e2625ceb4a4b7d6be4cc58241b27) | VERIFIED | Artefakty C03 Part A jsou přítomné a propojené s tracking issue a merged PR. |
| C03 Part B / architektura | Architektonické drivery, doménový model, views, runtime verification a design-to-code delta | Repository dokument; issue; PR; commit | [`docs/specification/C04/part-2.md`](specification/C04/part-2.md); [Issue #12](https://github.com/Zephy-X/AutaRepo/issues/12); [PR #11](https://github.com/Zephy-X/AutaRepo/pull/11); [commit `1e22e48`](https://github.com/Zephy-X/AutaRepo/commit/1e22e4840f4474d62b8f7716acde577b7fdb03aa) | PARTLY VERIFIED | Dokument má nadpis „C03 — Architecture Evidence“, ale je uložený pod `C04`; issue požadoval centralizaci v tomto evidence souboru, což dříve nebylo splněno. |
| C05 | Detailed design reservation lifecycle, kontrakty, odpovědnosti, strukturální/dynamický návrh a odvození testů | Repository dokument; issue; PR; tvrzení o implementaci/testech | [`docs/architecture-and-decisions.md#c05-detailed-design`](architecture-and-decisions.md#c05-detailed-design); [Issue #13](https://github.com/Zephy-X/AutaRepo/issues/13); [PR #11](https://github.com/Zephy-X/AutaRepo/pull/11) | PARTLY VERIFIED | C05 design section a tracking záznamy existují a PR #11 tvrdí unit i PostgreSQL verification. Samostatný C05 evidence index a nezávisle zopakovaný testovací záznam nebyl před touto opravou identifikován. |

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

### F3 — Traceability evidence byla neúplná a původní claim byl příliš široký

- Claim ID & Requirement: `F3` / traceability povinných výstupů C02, C03 a C05 ve formuláři a projektové evidenci.
- Reviewer Note: očekával se explicitní evidence section a přesné commit/tag záznamy pro C02/C03/C05.
- Our Verification: `VERIFIED FINDING`
- Disposition: `PARTLY ACCEPTED / CLAIM REFINED`
- Evidence & Resolution:
  - GitHub Issues dokazují, že práce na C02, C03 a C05 byla trackována: [#6](https://github.com/Zephy-X/AutaRepo/issues/6), [#8](https://github.com/Zephy-X/AutaRepo/issues/8), [#12](https://github.com/Zephy-X/AutaRepo/issues/12) a [#13](https://github.com/Zephy-X/AutaRepo/issues/13). Jejich propojené PR a commity poskytují další traceability.
  - Samotné issue tracking nedokazuje, že byl vytvořen a nezávisle ověřen každý povinný výstup.
  - Existující evidence je rozložena mezi `docs/baseline-v0.2-change-record.md`, `docs/specification/C03/`, `docs/specification/C04/part-2.md`, `docs/architecture-and-decisions.md` a propojené GitHub záznamy.
  - Původní finding proto zůstává platný jako traceability/documentation gap, ale tvrzení, že evidence C02/C03/C05 jednoduše chyběla, bylo příliš široké. C02 a C03 Part A jsou ověřené; C03 Part B a C05 zůstávají částečně ověřené kvůli pojmenování, centralizaci a mezeře v nezávislém ověření.
  - Fix: doplněn výše uvedený evidence index a ekvivalentní český index s přesnými cestami, URL, commity, statusy a zbývajícími mezerami. Repository tag pro tyto deliverables nebyl nalezen.

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
- Dokumentační mezera F3: claim byl zpřesněn, nikoli zamítnut; GitHub tracking je odděleně přiznán od repository evidence a zbývající mezery C03 Part B/C05 jsou explicitně zaznamenány.
- Automatická verifikace: `./mvnw.cmd test` skončila úspěšně s kódem `0`.

---

## C08 — Before baseline a testovací evidence

Karta změny, dopady a varianty: [`intent-and-change-cz.md#c08--řízená-změna`](intent-and-change-cz.md#c08--řízená-změna). Anglická verze (autoritativní): [`evidence-and-evolution.md#c08--before-baseline-and-test-evidence`](evidence-and-evolution.md#c08--before-baseline-and-test-evidence).

### Krok 0 — Zmrazený before baseline

| Položka | Hodnota |
|---|---|
| Commit | `c8db481f5913753e2e86abb5791ebbb875e78963` (`main`). Produkční kód v `src/main` je **beze změny** |
| Přidáno pro C08 (jen test) | `ReservationC08BaselineIntegrationTest.java` (commitnout spolu s dokumenty) |
| Přijatá specifikace | Baseline v0.3: BR-01, BR-02, BR-03, BR-06; OP-03, OP-04, OP-05; `state-diagram.mmd`; ADR-01 + C05 |
| Runtime při běhu | OpenJDK **26.0.2.1** (pom cílí na **21**; pro přesnou reprodukci použijte JDK 21), Maven 3.9.16 (`./mvnw`), Spring Boot **4.1.1**, Windows 11 |
| Testovací DB | (a) H2 2.4.240 in-memory `c08baseline`, PostgreSQL mode, `create-drop`; (b) PostgreSQL **16.15** (`postgres:16` z `docker-compose.yml`), samostatná jednorázová DB `autorepo_c08`. Vývojová `autorepodb` **nebyla** dotčena |
| Verze schématu | Bez migrací; schéma generuje Hibernate z entit na `c8db481`. Časy `timestamp(6) without time zone`, `state` s CHECK na 6 hodnot, indexy `idx_reservation_range`, `idx_reservation_vehicle_state` |
| Hodiny | `Clock.fixed(2026-12-01T06:00:00Z, UTC)` jako `@Primary` bean; interval scheduleru 1 h |
| Izolace / data | Bez `@Transactional` na třídě; každé volání služby commituje vlastní transakci. `@BeforeEach` smaže vše a vloží uživatele a standardní vozidlo **R1**. Orákly (očekávané výsledky) čtou commitnuté řádky v nových transakcích |
| Příkazy | [C08 — Reprodukovatelné testovací příkazy](#c08--reprodukovatelné-testovací-příkazy) níže |

### Stávající sada na baseline (spuštěno 2026-11-15)

- Bez Dockeru: `Tests run: 21, Failures: 0, Errors: 1`. Chyba je `ReservationPersistenceSpikeTest`, který potřebuje PostgreSQL. Jde o podmínku prostředí, ne o defekt.
- S běžícím `postgres` a novou C08 třídou: `Tests run: 25, Failures: 0, Errors: 0, Skipped: 1`, **BUILD SUCCESS**. Log: [`evidence/c08/run4-full-suite-c8db481.txt`](evidence/c08/run4-full-suite-c8db481.txt)

### C08 / TC-C08-01 — pozitivní: sousední potvrzení zachová BR-02

- **Testovací báze:** BR-01, BR-02, OP-03 REQ-03 @ v0.3; riziko R-01 (jen sekvenční část).
- **Předpoklady:** B1 `CONFIRMED` R1 `[2026-12-01T10:00, 11:00)`, B2 `DRAFT` R1 `[11:00, 12:00)`, čas `06:00 UTC`. Podmínka role: **N/A (BLOCKED)**, model rolí neexistuje.

| # | Akce | Očekáváno PŘED během | Skutečnost PO běhu | Evidence |
|---|---|---|---|---|
| 1 | Reset, vložení R1, B1, B2 | 1× `CONFIRMED`, 1× `DRAFT` | Dle očekávání | `@BeforeEach` |
| 2 | Nezávislé čtení stavu | B1 `CONFIRMED`, B2 `DRAFT`, 1 potvrzená | Dle očekávání | aserce |
| 3 | `confirmReservation(B2)` | Uspěje, `CONFIRMED` | `CONFIRMED`, notifikace „confirmed" v logu | logy |
| 4 | Čtení commitnutých `CONFIRMED`, kontrola překryvu | {B1, B2}, bez překryvu | Dle očekávání | `assertNoOverlappingConfirmed` |
| 5 | Opakování po resetu | Stejně | Stejně (H2 i PostgreSQL) | run1, run2 |

- **Verdikt:** **PASS**.

### C08 / TC-C08-02 — negativní: překrývající potvrzení je odmítnuto

- **Předpoklady:** B1 `CONFIRMED` `[10:00, 11:00)`, B2 `DRAFT` `[10:30, 11:30)`, čas `06:00 UTC`. Kontrakt: `ConflictOverlapException("CONFLICT_OVERLAP")` → flash zpráva (SSR, žádné HTTP 409).

| # | Akce | Očekáváno PŘED během | Skutečnost PO běhu | Evidence |
|---|---|---|---|---|
| 1 | Reset, vložení | Jen B1 `CONFIRMED` | Dle očekávání | `@BeforeEach` |
| 2 | Čtení výchozího stavu | Překryv časů, 1 potvrzená | Dle očekávání | aserce |
| 3 | `confirmReservation(B2)` | `CONFLICT_OVERLAP` | Vyhozeno dle očekávání | `assertThatThrownBy` |
| 4 | Čtení po transakci | B1 `CONFIRMED`, B2 `DRAFT` | Dle očekávání | aserce |
| 5 | Opakování | Stejně | Stejně (H2 i PostgreSQL) | run1, run2 |

- **Verdikt:** **PASS**.

### C08 / TC-C08-03 — současné chování storna („before" pro CR-08)

| # | Akce | Očekáváno PŘED během | Skutečnost PO běhu |
|---|---|---|---|
| 1 | Storno `CONFIRMED` `[07:00, 09:00)` v `start − 1h` | Povoleno (v0.3) → `CANCELLED` | `CANCELLED` |
| 2 | Storno `CONFIRMED` `[06:00, 08:00)` v `now == start` | `CANCELLATION_WINDOW_CLOSED`, stav `CONFIRMED` | Dle očekávání |

- **Verdikt:** **PASS**. Po CR-08 se krok 1 **musí překlopit** na `CANCELLATION_CUTOFF_REACHED`.

### C08 / TC-C08-04 — sonda: pozdní schválení uloží `EXPIRED`

- **Předpoklady:** luxusní vozidlo, P `PENDING_APPROVAL` `[10:00, 12:00)`, `reviewDeadline = 05:00`, čas `06:00 UTC`.

| # | Akce | Očekáváno PŘED během | Skutečnost PO běhu | Evidence |
|---|---|---|---|---|
| 1 | `approveReservation(P)` | `APPROVAL_WINDOW_EXPIRED` | Vyhozeno; notifikace „expired" odeslána | run1, run3 |
| 2 | Čtení commitnutého stavu | `EXPIRED` | **`PENDING_APPROVAL`** | run1 (H2), run3 (PostgreSQL) |

- **Verdikt:** **FAIL** → **DEF-C08-01** (rollback `@Transactional` metody). Test je teď `@Disabled` s popisem pozorování a lze ho spustit přes `-Djunit.jupiter.conditions.deactivate=org.junit.*DisabledCondition`. C09 `@Disabled` odstraní.

### C08 — Reprodukovatelné testovací příkazy

**1. C08 testy na izolované in-memory H2 (bez Dockeru):**

```bash
./mvnw -B test -Dtest=ReservationC08BaselineIntegrationTest
```

**2. Stejné testy na skutečném PostgreSQL 16, v samostatné jednorázové databázi** (test maže všechny řádky, nikdy ho nesměrujte na `autorepodb`):

```bash
docker compose up -d postgres
docker exec local_postgres psql -U postgres -c "CREATE DATABASE autorepo_c08;"
# přihlašovací údaje = výchozí z docker-compose (viz README); C08_DB_PASSWORD nastavte sami
export C08_DB_URL=jdbc:postgresql://localhost:5432/autorepo_c08
export C08_DB_DRIVER=org.postgresql.Driver
export C08_DB_USER=postgres
export C08_DB_PASSWORD=<heslo z compose>
export C08_DB_DIALECT=org.hibernate.dialect.PostgreSQLDialect
./mvnw -B test -Dtest=ReservationC08BaselineIntegrationTest
```

**3. Spuštění vypnuté sondy TC-C08-04 (DEF-C08-01, do C09 má selhat):**

```bash
./mvnw -B test -Dtest=ReservationC08BaselineIntegrationTest "-Djunit.jupiter.conditions.deactivate=org.junit.*DisabledCondition"
```

**4. Celá sada** (`ReservationPersistenceSpikeTest` potřebuje běžící `postgres` z compose, jinak skončí *Failed to load ApplicationContext*):

```bash
./mvnw -B test
```

V PowerShellu použijte `./mvnw.cmd` a `$env:C08_DB_URL="..."` místo `export`.

### C08 — Definition of Done

- [x] Přesný before baseline a jeho přijaté chování (`c8db481`, Baseline v0.3)
- [x] Dva krokové testovací případy s daty a očekáváním zapsanými před během (TC-C08-01/02, navíc TC-C08-03/04)
- [x] Alespoň jeden skutečně spuštěný přes reálnou persistenci: všechny čtyři na H2 **i** PostgreSQL 16.15 (TC-04 FAIL → DEF-C08-01)
- [x] Jedno věrohodné CR (CR-08) s úplnou maticí dopadů a maticí variant
- [x] Rozhodnutí zapsáno: ACCEPT varianta A, vlastník Radim Pukalík, 2026-11-15
- [ ] **Peer review:** Adam Murcko napíše review do karty změny (`intent-and-change-cz.md` → Peer review → `TODO`)
- [x] Konkrétní předání do C09 (delta, orákly (očekávané výsledky), regrese)
- [x] Odkazy žijí ve stávajícím repozitáři; známá omezení zapsána

### Shrnutí stavu C08

- TC-C08-01/02/03 **PASS**, TC-C08-04 **FAIL → DEF-C08-01**.
- CR-08: **ACCEPTED, varianta A** (vlastník Radim Pukalík, 2026-11-15; recenzent Adam Murcko, review čeká). PLANNED pro C09: neimplementováno, neověřeno.
- DEF-C08-01: **ACCEPTED** jako oprava defektu v C09 (vlastník Radim Pukalík).
- Nespuštěno: test souběhu (R-01), HTTP/UI toky, kroky s rolí (BLOCKED).
