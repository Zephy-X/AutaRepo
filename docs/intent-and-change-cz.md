# Projektový rámec (Baseline v0.3)

## Rezervační doména

Sdílený vozový park s rezervacemi vozidel a diferencovanou správou luxusních / omezených vozidel, výhradními soft-locky a automatickým background expiration.

## Účel

Webová rezervovací platforma pro jednotlivce a organizace, které zváží dostupnost a rezervují vozidla. Odstraňuje konflikty v plánování, zaručuje výhradní dostupnost pro plánované časy vyzvednutí, zabraňuje dvojímu rezervování díky předběžným soft-lockům a přepíná omezené / luxusní žádosti přes autorizovanou schvalovací bránu ještě před konečným závazkem k alokaci.

## Uživatelé / Stakeholdeři

- **User / Renter**: prohlíží inventář, vytváří draft rezervace, odesílá rezervace k okamžité potvrzení nebo k přezkoumání, a může zrušit přípustné rezervace před odjezdem.
- **Fleet Manager / Approver**: zdědí všechny standardní možnosti uživatele; dohlíží na provozní stav vozidel (`isActive`) a přezkoumává, schvaluje nebo odmítá čekající žádosti pro vozidla vyšší třídy / luxusní vozidla (`requiresApproval`).
- **System (Scheduler / Background Worker)**: automatický background actor odpovědný za vynucování review SLA a spouštění timeoutů (`OP-07`) pro nevyřízené žádosti.

## Klíčové koncepty

- **Resource (Vehicle)**: rezervované auto (`id`, `brand`, `model`, `licensePlate`, `isActive`, `requiresApproval` / luxusní klasifikace).
- **Reservation**: záznam rezervace s polouzavřeným časovým rozsahem `[startTime, endTime)`, vazbou na Resource a User a provozním lifecycle statusem.
- **User**: klient, který vytváří a interaguje s rezervacemi (včetně role a příznaků approver privileges).
- **ReservationState**: lifecycle enum (`DRAFT`, `PENDING_APPROVAL`, `CONFIRMED`, `REJECTED`, `CANCELLED`, `EXPIRED`).
- **Soft-Lock**: výhradní předběžné držení vytvořené během `PENDING_APPROVAL`, které blokuje `OP-02 Availability` a zabraňuje jinému uživateli zabrat slot během čekání na review.
- **Hard-Lock**: trvalé, závazné alokování (`CONFIRMED`), které zaručuje vozidlo pro plánovaný interval.

## Základní operace (Baseline v0.3)

- **OP-01 Create Reservation**: Uživatelské zaznamenání záměru o rezervaci vytvořením nové rezervace ve stavu `DRAFT`. Není přidělena žádná alokace ani inventory lock.
- **OP-02 Check Availability**: read-only dotaz vyhodnocující, zda je exklusivní vozidlo dostupné pro požadovaný polouzavřený interval `[start, end)`. Jakýkoli překrývající se interval v `CONFIRMED` nebo `PENDING_APPROVAL` je považován za nedostupný.
- **OP-03 Confirm Reservation**: vyhodnocuje konflikty napříč záznamy v `CONFIRMED` a `PENDING_APPROVAL`. Převede způsobilé standardní `DRAFT` rezervace přímo do závazné alokace (`CONFIRMED`). U vozidel vyžadujících schválení (`requiresApproval == true`) vytvoří výhradní **soft-lock** a přepne záznam do `PENDING_APPROVAL`.
- **OP-04 Cancel Reservation**: Stornuje `DRAFT`, `PENDING_APPROVAL` nebo `CONFIRMED` rezervaci před jejím startem (`currentTime < startTime`) a převede ji do `CANCELLED`; přitom okamžitě uvolní soft-lock nebo hard-lock zpět do poolu dostupnosti.
- **OP-05 Approve Reservation**: Oprávněný approver přezkoumá `PENDING_APPROVAL` rezervaci a přemění existující soft-lock na závaznou alokaci (`CONFIRMED`), čímž vozidlo výhradně zablokuje podle `BR-02`.
- **OP-06 Reject Reservation**: Oprávněný approver žádost odmítne a přepne ji do `REJECTED`; soft-lock se okamžitě rozpadne.
- **OP-07 Expire Pending Reservation**: Automatická systémová operace spuštěná plánovačem, když nevyřízená žádost dosáhne deadlinu (`currentTime >= reviewDeadline` nebo `currentTime >= startTime`), přepne stav do `EXPIRED` a uvolní soft-lock.
- **Manage Fleet (Resource Admin)**: úkony pro označení vozidel jako aktivní/neaktivní (`isActive = true/false`) a konfiguraci požadavků na autorizaci (`requiresApproval = true/false`).

## Trvalý stav

- **Vehicle**: `id` (PK), `brand`, `model`, `vin`, `licensePlate`, `isActive` (boolean), `requiresApproval` (boolean flag označující požadavek na schvalovací bránu).
- **User**: `id` (PK), `fullName`, `email`, `role` (enum / identifikátor approver privilegií).
- **Reservation**: `id` (PK), `vehicle_id` (FK), `user_id` (FK), `startTime` (timestamp), `endTime` (timestamp), `state` (enum: `DRAFT`, `PENDING_APPROVAL`, `CONFIRMED`, `REJECTED`, `CANCELLED`, `EXPIRED`), `reviewDeadline` (timestamp, nastaven při odeslání do `PENDING_APPROVAL`), `approver_id` (FK, nullable), `approvedAt` (timestamp, nullable), `submittedAt` (timestamp, nullable), `createdAt` (timestamp).

## Stavové operace

- `[none]` → `DRAFT` (`OP-01` Create Reservation)
- `DRAFT` → `CONFIRMED` (`OP-03` Confirm Reservation, pouze standardní vozidla; vynucuje kontrolu konfliktu podle `BR-02`)
- `DRAFT` → `PENDING_APPROVAL` (`OP-03` Confirm Reservation, spuštěno tehdy, pokud `vehicle.requiresApproval == true`; získá výhradní soft-lock)
- `PENDING_APPROVAL` → `CONFIRMED` (`OP-05` Approve Reservation, vykonáván schvalovatelem; přemění soft-lock na hard allocation)
- `PENDING_APPROVAL` → `REJECTED` (`OP-06` Reject Reservation, vykonává schvalovatel; uvolní soft-lock)
- `PENDING_APPROVAL` → `EXPIRED` (`OP-07` Expire Pending Reservation, automatický přechod, když `currentTime >= reviewDeadline` nebo `currentTime >= startTime`; uvolní soft-lock)
- `DRAFT` → `CANCELLED` (`OP-04` Cancel Reservation, zrušení uživatelem)
- `PENDING_APPROVAL` → `CANCELLED` (`OP-04` Cancel Reservation, zrušení uživatelem před startem; uvolní soft-lock)
- `CONFIRMED` → `CANCELLED` (`OP-04` Cancel Reservation, uvolní vložené alokování vozidla)

## Společná obchodní pravidla a invarianty

- **BR-01 (Interval Semantics)**: Všechny intervaly rezervací používají sémantiku polouzavřeného intervalu `[startTime, endTime)`. Sousední rezervace začínající v `endTime` se nekoliduje. Intervaly vyžadují striktní `startTime < endTime`.
- **BR-02 (Exclusive Resource Invariant & Blocking States)**: V žádném committed stavu systému nesmí pro stejné vozidlo překrývat dvě blokující rezervace:

  $$
  \max(A.start, B.start) < \min(A.end, B.end)
  $$

  `CONFIRMED` (hard-lock) i `PENDING_APPROVAL` (soft-lock) jsou blokující stavy. Neblokující stavy jsou `DRAFT`, `CANCELLED`, `REJECTED` a `EXPIRED`.
- **BR-03 (Cancellation Policy)**: Rezervaci ve stavu `DRAFT`, `PENDING_APPROVAL` nebo `CONFIRMED` lze zrušit jen tehdy, pokud `currentTime < Reservation.startTime`. Přechod do `CANCELLED` okamžitě uvolní všechny inventory zámky. Terminalní stavy nelze znovu zrušit.
- **BR-04 (Draft Non-Allocation)**: `DRAFT` rezervace nevyžaduje a neblokuje dostupnost vozidla v `OP-02`. Překrývající se drafty soutěží na principu first-to-confirm při `OP-03`.
- **BR-05 (Luxury Vehicle Policy & Authorization Lifecycle)**: Vozidlo s `requiresApproval == true` nesmí přejít přímo z `DRAFT` do `CONFIRMED`. Při `OP-03` přejde do `PENDING_APPROVAL`, kde získá výhradní soft-lock až do explicitního vykonání `OP-05` (Approve) nebo `OP-06` (Reject) schvalovatelem.
- **BR-06 (Time-to-Live & Automatic Expiration)**: Každá rezervace v `PENDING_APPROVAL` má review window definovaný jako $\text{reviewDeadline} = \min(\text{submittedAt} + \Delta T_{\text{SLA}}, \text{Reservation.startTime})$. Pokud `currentTime >= reviewDeadline` nebo `currentTime >= startTime` bez rozhodnutí manažera, `OP-07` přepne záznam do `EXPIRED` a uvolní soft-lock.
- **BR-07 (Resource Exclusivity Assumption)**: Každé vozidlo je diskrétní fyzická entita s kapacitou = 1. Sdílené a zlomkové alokace nejsou v rozsahu.

## Doménově specifické obchodní pravidlo

- **BR-Domain (Lead Time & Max Duration)**: Rezervaci nelze vytvořit v minulosti (`startTime > currentTime`) a délka rezervace musí striktně splňovat `0 < (endTime - startTime) <= 14 days`.

## Externí / systémová hranice

- **Notification Service**: externí messaging boundary, který dostává upozornění, když rezervace přejde do `PENDING_APPROVAL`, `CONFIRMED`, `REJECTED`, `CANCELLED` nebo `EXPIRED`.
- **System Clock / Scheduler**: externí časový zdroj a worker daemon, který řídí časové invarianty a spouští `OP-07` automatické sweeps expirace.

## Předpoklady

- Schvalovatelé dědí všechny oprávnění standardních uživatelů přes generalizaci actorů.
- Background worker pro `OP-07` běží na pevně daných cron intervalech a zároveň je doplněn lazy evaluation kontrolami při přístupu.
- Pro všechny časové porovnání se používají standardní UTC timestamps.

## Vyřešené architektonické neznámé (z v0.2)

- **Pending Hold Semantics**: Vyřešeno. `PENDING_APPROVAL` ukládá výhradní **soft-lock** na zdroj. Blokuje `OP-02 Availability` a zabraňuje konkurujícím rezervacím zabrat slot během vyřizování manažerského rozhodnutí.
- **Stale Reservation Expiration**: Vyřešeno. Omezeno automatickým review SLA deadlinem přes `OP-07`, aby se předešlo tomu, že auto zůstane v soft-locked limbu.

## Vybrané budoucí tlaky

- **Kategorie**: Q (Quality / Concurrency, Contention & Temporal Consistency)
- **Konkrétní tlak**: vysokou konkurencí a race conditions během `OP-03` potvrzení, kdy se více uživatelů pokouší potvrdit překrývající se `DRAFT` rezervace pro stejné vozidlo současně.
- **Relevance pro náš systém**: I když soft-locking eliminuje kolize během schválení (`OP-05`), počáteční potvrzovací krok (`OP-03`) musí používat databázovou izolaci (např. serializable transakce, `SELECT ... FOR UPDATE` row locks nebo exclusion constraints na `[startTime, endTime)`) tak, aby pouze jedna současná transakce úspěšně získala vozidlo do `CONFIRMED` nebo `PENDING_APPROVAL`.
