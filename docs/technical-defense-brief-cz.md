# Technický obhajobový brief — Confirm Reservation

**Aktuální reporting commit:** `1012afad99641aac84f8fb674f77d1532ccb991d` (`feat: add C07 (#14)`)  
**Aktualizace ověření:** celý Maven test suite prošel při spuštěném Docker Compose včetně testů používajících PostgreSQL.

## 1) Rozsah a invariant

Obhajujeme cestu `OP-03 Confirm Reservation` a hlavní invariant `BR-02`:
dvě blokující rezervace stejného vozidla se nesmí překrývat.

- Rozsah: `ReservationService.confirmReservation(Long id)`
- Invariant: pro žádnou dvojici rezervací ve stavech `CONFIRMED` nebo
  `PENDING_APPROVAL` stejného vozidla nesmí platit
  `max(A.start, B.start) < min(A.end, B.end)`.
- Zdroj požadavků: `docs/specification/operations/03-confirm-reservation/OP-03-confirm-reservation.md`
  a `docs/business-rules.md`.
- Kontrolované předpoklady: rezervace existuje, je ve stavu `DRAFT`, vozidlo
  je aktivní a `currentTime < reservation.startTime`.

## 2) Architektura: AS-IS a TO-BE

### AS-IS (akceptovaný základ)

- `ReservationViewController` předává požadavky do servisní vrstvy.
- `ReservationService` rozhoduje o potvrzení, schválení, zamítnutí, expiraci a
  zrušení rezervací.
- `ReservationRepository` poskytuje konfliktní dotaz
  `hasStateConflict` a ukládá změny.
- `VehicleService.findByIdForUpdate` získává zámek řádku vozidla používaný
  současným návrhem pro řízení konkurence.
- `NotificationServiceStub` posílá vedlejší účinky po změně stavu.

### TO-BE (současný pracovní návrh)

- Autorita pro životní cyklus zůstává v `ReservationService`.
- Agregát `Reservation` kontroluje lokální invarianty stavu a aktivity zdroje.
- `ReservationRepository` zůstává hranicí persistence s overlap dotazem.
- Scheduler používá stejnou servisní metodu
  `expireStaleReservations()`.

Současná implementace je funkčně ověřená v lokálním Docker/PostgreSQL
prostředí. Tvrzení o konkurenci však ještě nemá úplný důkaz: neexistuje
dedikovaný stress test, který by ukázal výsledek dvou souběžných PostgreSQL
transakcí potvrzujících rezervaci.

### Hranice odpovědností

- Rozhodování: `ReservationService`
- Persistence: `ReservationRepository`
- Notifikace: `NotificationServiceStub`
- Controller: pouze převod požadavku, bez business logiky

## 3) Detailní návrh

### Volání

`ReservationService.confirmReservation(id)`:

1. Načte rezervaci podle ID.
2. Ověří stav `DRAFT`.
3. Zamkne vozidlo přes `findByIdForUpdate`.
4. Ověří `vehicle.isActive()` a `now < startTime`.
5. Spustí `hasStateConflict(vehicleId, start, end, ignoreReservationId)`.
6. Podle politiky zavolá `reservation.requestApproval(now)` nebo
   `reservation.confirm(now)`.
7. Provede `saveAndFlush`.
8. Odešle notifikaci na externí hranici.

### Důležité metody a chyby

- `Reservation.confirm(LocalDateTime now)` kontroluje stav, aktivitu vozidla a
  budoucí začátek.
- `Reservation.requestApproval(LocalDateTime now)` používá stejné ochrany a
  nastaví `submittedAt` a `reviewDeadline`.
- `ReservationService.ensureNoBlockingConflict(...)` při konfliktu vyhodí
  `ConflictOverlapException`.
- `ReservationService.approveReservation(...)` řeší expiraci při
  `now >= reviewDeadline || now >= startTime`.

## 4) Spustitelné ověření

### Pozitivní test

Příkaz:

`.\mvnw.cmd -q -Dtest=ReservationServiceTest#routesApprovalRequiredVehicleToPendingApproval test`

Očekávaný výsledek:

- existující draft přejde do `PENDING_APPROVAL`,
- odešle se jedna notifikace o čekajícím schválení,
- neodešle se notifikace `CONFIRMED`.

Test prošel a přechod stavu proběhl podle očekávání.

### Negativní / hraniční test

Příkaz:

`.\mvnw.cmd -q -Dtest=ReservationServiceTest#rejectsConfirmationForInactiveVehicle test`

Očekávaný výsledek:

- vyhodí se `RESOURCE_INACTIVE`,
- rezervace zůstane ve stavu `DRAFT`,
- notifikace se neodešle.

Test prošel; neplatný přechod byl zablokován.

### Celý cílený suite

Příkaz:

`.\mvnw.cmd -q -Dtest=ReservationServiceTest test`

Výsledek: exit code `0`; cílené testy pokrývají požadavek, schválení,
expiraci, zrušení a nové kontroly vozidla a času.

### Celý test suite s Docker/PostgreSQL

Příkaz:

`.\mvnw.cmd -q test`

Prostředí a výsledek:

- Docker Compose běžel a `local_postgres` byl healthy na portu `5432`.
- Persistence test se připojil na PostgreSQL `16.15`.
- Hibernate použil `PostgreSQLDialect`.
- Celý Maven test suite skončil s exit code `0`.
- Předchozí chyba s JDBC metadaty byla způsobena zastaveným PostgreSQL
  kontejnerem.

Tento výsledek potvrzuje, že integrační cesta funguje s dostupnou databází.
Sám o sobě však nepotvrzuje bezchybný výsledek dvou současných potvrzovacích
transakcí ani nenahrazuje samostatný runtime test schedulerové expirace.

## 5) Obrana nálezů C06

### F1 — akceptovaný nález

Původní baseline nekontrolovala při potvrzení aktivitu vozidla a budoucí čas
začátku rezervace. Oprava je v `ReservationService.confirmReservation(...)`
a v ochranách agregátu `Reservation`.

### F2 — opravený nesoulad návrhu a implementace

Původní implementace duplikovala pravidla v `ReservationService` a používala
obecnou chybu místo `ConflictOverlapException`. Service nyní deleguje validaci,
expiraci a zrušení přes `ReservationRules` a konflikt používá doménovou
výjimku.

### F3 — opravená dokumentační mezera

Byly doplněny baseline/reporting identifikátory, audit nálezů C06 a evidence
v `docs/evidence-and-evolution.md`.

## Závěrečné vyjádření

Implementace nyní vynucuje ochrany `OP-03` a overlap rozhodování a celý test
suite prochází s PostgreSQL spuštěným v Dockeru. Status zůstává `AMBER`,
protože ještě chybí samostatný důkaz PostgreSQL konkurence, runtime důkaz
schedulerové expirace a reprodukovatelnost na čistém druhém prostředí.
