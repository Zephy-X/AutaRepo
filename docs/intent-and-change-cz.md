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

---

# C08 — Řízená změna

> **Proč ne vzorový příklad (expirace čekajícího schválení)?** Náš baseline ho už implementuje: BR-06 / OP-07, `ReservationRules.shouldExpire`, lazy kontrola v `ReservationService.approveReservation` a `ReservationExpirationScheduler`. Znovu ho specifikovat by nebyla změna. Postup C08 proto aplikujeme na **změnu pravidla storna (CR-08)**. Krok 2 navíc odhalil skutečný defekt ve stávající cestě expirace (**DEF-C08-01**). Je zapsán zvlášť, protože jde o defekt vůči přijaté specifikaci, ne o nový požadavek.
>
> Testovací případy a evidence: [`evidence-and-evolution-cz.md#c08--before-baseline-a-testovací-evidence`](evidence-and-evolution-cz.md#c08--before-baseline-a-testovací-evidence). Reprodukovatelné příkazy: [`evidence-and-evolution-cz.md#c08--reprodukovatelné-testovací-příkazy`](evidence-and-evolution-cz.md#c08--reprodukovatelné-testovací-příkazy). Anglická verze je autoritativní: [`intent-and-change.md#c08--controlled-change`](intent-and-change.md#c08--controlled-change).

## C08 / CR-08 — Karta řízené změny: lhůta storna potvrzených rezervací

**Stav:** `ACCEPTED — varianta A` (vlastník Radim Pukalík, 2026-11-15). Peer review Adama Murcka **čeká** (viz Peer review). CR-08 je **PLANNED pro C09**: neimplementováno, neověřeno (PLANNED ≠ IMPLEMENTED ≠ VERIFIED).

### Zdroj změny a konkrétní potřeba

- **Zdroj:** stakeholder Fleet Manager / Approver.
- **Potřeba:** „Když nájemce zruší potvrzené auto těsně před vyzvednutím, auto je už připravené (umyté, natankované, klíče na pultu) a nestihneme ho nabídnout nikomu jinému. Potvrzenou rezervaci nesmí jít zrušit na poslední chvíli."
- **Cíl:** zaručit vozovému parku minimální okno pro přerozdělení před závazným (hard-lock) vyzvednutím.

### Before baseline

| Položka | Hodnota |
|---|---|
| Commit | `c8db481f5913753e2e86abb5791ebbb875e78963` (`main`, 2026-11-08) |
| Přijatá specifikace | Baseline v0.3: **BR-03**, **OP-04** (REQ-05), statechart `state-diagram.mmd`, `business-rules-cz.md#br-03` |
| Architektonické rozhodnutí | ADR-01 + C05: `ReservationRules` vlastní pravidla, `ReservationService` vlastní přechody |
| Schéma | Bez verzovaných migrací. Schéma generuje Hibernate z JPA entit na `c8db481` |
| Konfigurace | `MIN_LEAD_TIME = 2h`, `MAX_DURATION = 14d`, `REVIEW_SLA = 24h` napevno v `ReservationRules`; bean `Clock.systemUTC()`; `autorepo.expiry-interval` (default 60 000 ms) |

### Předchozí pozorovatelné chování

BR-03 v0.3 / REQ-05: `DRAFT`, `PENDING_APPROVAL` a `CONFIRMED` lze zrušit **právě když `currentTime < startTime`**. Potvrzenou rezervaci tedy lze zrušit minutu před vyzvednutím. Ověřeno spuštěním **TC-C08-03** (storno v `start − 1h` → `CANCELLED`).

### Nové zamýšlené chování, rozsah a mimo rozsah

- **Nové pravidlo (návrh BR-03 v0.4):** rezervaci ve stavu `CONFIRMED` lze zrušit **právě když `currentTime < startTime − CANCELLATION_CUTOFF`**, kde `CANCELLATION_CUTOFF = 2 h` (stejně jako `MIN_LEAD_TIME`). Pozdní pokus je odmítnut a stav zůstává `CONFIRMED`.
- **Beze změny:** `DRAFT` a `PENDING_APPROVAL` mají dál `currentTime < startTime`.
- **Časová sémantika:** serverový čas z injektovaného `Clock` (UTC). `startTime` je uložen jako `timestamp without time zone` a vykládá se jako UTC (R-05).
- **Mimo rozsah:** storno poplatky, nový stav `LATE_CANCELLED`, override approverem (role neexistují), konfigurovatelná nebo per-vozidlo lhůta, scheduler, redesign UI.

### Akceptační podmínky

Fixní serverové hodiny, exkluzivní vozidlo R1, rezervace `B` ve stavu `CONFIRMED` se `startTime = S`.

| ID | Typ | Podmínka |
|---|---|---|
| AC-01 | pozitivní | V `now = S − 2h − 1min` storno uspěje: commitnutý stav `CANCELLED`, OP-02 hlásí `AVAILABLE`. |
| AC-02 | negativní | V `now = S − 1h` je storno odmítnuto **samostatným** kódem `CANCELLATION_CUTOFF_REACHED` se zprávou „Confirmed reservations cannot be cancelled less than 2 hours before start." Stav zůstává `CONFIRMED`, interval `UNAVAILABLE`. |
| AC-03 | hranice | **Přesně** v `now = S − 2h` je storno **odmítnuto** (jako AC-02). Polootevřené okno `now < S − 2h`, konzistentní s BR-01. |
| AC-04 | hranice | V `now = S` stále `CANCELLATION_WINDOW_CLOSED`. |
| AC-05 | regrese | `DRAFT` a `PENDING_APPROVAL` v `now = S − 1h` lze zrušit; v `now = S` → `CANCELLATION_WINDOW_CLOSED`. |
| AC-06 | regrese | BR-02 platí: TC-C08-01 a TC-C08-02 dávají stejné verdikty. |
| AC-07 | regrese | Terminální stavy beze změny: re-cancel → `ALREADY_CANCELLED`; `REJECTED`/`EXPIRED` → `INVALID_STATE_TRANSITION`. |

### Metoda dopadové analýzy

Od AC-02 → vlastník rozhodnutí (`ReservationRules.canCancel`, volaný z `ReservationService.cancelReservation` **i** z `Reservation.cancel`/`isCancellable`) → volající v controlleru, šablonách, persistenci a testech → další zapisovatelé `state` → konfigurace, release, provoz. Zkontrolované artefakty jsou vyjmenované v anglické verzi.

| Dimenze / místo | CHANGE / CHECK / NO IMPACT a proč | Akce | Vlastník / evidence |
|---|---|---|---|
| Specifikace BR-03, OP-04/REQ-05, flow `.mmd`, summary table (EN + CZ) | **CHANGE**: mění se pravidlo pro `CONFIRMED` | BR-03 v0.4 s AC-01..07 | Spec owner; diff v C09 PR |
| Stavový model | **CHANGE**: guard `CONFIRMED → CANCELLED` = `[currentTime < start − 2h]`; žádný nový stav | Upravit popisek | Spec owner |
| Architektura (ADR-01, C05) | **CHECK**: vlastník se nemění; v C05 přibude nový chybový kód u `cancelReservation(id)` | Upravit řádek kontraktu | Architekt |
| `ReservationRules.canCancel` (statická, volá ji i entita) | **CHANGE**: konstanta `CONFIRMED_CANCELLATION_CUTOFF` + okno podle stavu | Unit kontrakt | Vývojář; `ReservationRulesTest` |
| `Reservation.cancel` + `ReservationService.cancelReservation` (guard je **duplicitní**) | **CHANGE**: obě místa musí vracet `CANCELLATION_CUTOFF_REACHED` | Nová výjimka nebo nový kód (rozhodne C09) | Vývojář; `ReservationServiceTest` |
| `DomainErrorMessages`, `ReservationViewController.handleAction`, `GlobalExceptionHandler` | **CHANGE**: nový kód + zpráva; novou výjimku je třeba chytat, jinak skončí na obecné 500 | Mapování + catch | Vývojář; integrační test flash zprávy |
| UI `list.html:217-219` (`#temporals.createNow()`), `detail.html:40` | **CHANGE**: tlačítko by bylo aktivní i v lhůtě; `createNow()` používá lokální čas JVM, ne UTC `Clock` (R-05) | Povolení řídit příznakem ze služby | Vývojář; ruční UI kontrola |
| Data / schéma | **CHECK → beze změny schématu**: pravidlo se vyhodnocuje při volání; stávající `CONFIRMED` rezervace do 2 h nebude po vydání možné zrušit (záměrně) | Release note | Release owner |
| Další zapisovatelé `state` | Scheduler → jen `PENDING_APPROVAL` (**NO IMPACT**); `DataInitializer` neruší (**NO IMPACT**); **přímé SQL přes pgAdmin je reálný zapisovatel**. DB `CHECK` nedokáže vyjádřit pravidlo vůči `now()` | Deklarované omezení: ruční SQL jen jako auditovaná provozní činnost | Ops owner |
| Konfigurace / CI / release | **CHECK**: lhůta napevno (A) nebo property (B); **CI pipeline neexistuje** (`docker-image.yml` odstraněn v `c8db481`) | Hodnotu zapsat do BR-03 a README; C09 clean checkout `./mvnw test` | Project lead |
| Testy | **CHANGE**: TC-C08-03 ověřuje *staré* chování a **musí se překlopit**; `ReservationRulesTest:63` a `cancelsBeforeStartAndNotifies` zůstávají platné; nové testy AC-01..05 | Úprava TC-03 + nové orákly (očekávané výsledky) | Vývojář; běh na C09 commitu |
| OP-01/02/03/05/06/07 | **NO IMPACT** v kódu; **CHECK** regrese | Celá sada | Vývojář |
| Provoz / podpora | **CHECK**: pozdní storno nemá samoobslužnou cestu, řeší ho fleet manager mimo systém | Text zprávy odkáže na fleet managera | Product owner |

### Alternativy

| Varianta | Princip | Splňuje AC? | Přínos | Náklad / selhání / návrat | Ověření |
|---|---|---|---|---|---|
| **A: konstanta + guard u stávajícího vlastníka** | `CONFIRMED_CANCELLATION_CUTOFF = 2h`, `canCancel` podle stavu, kód `CANCELLATION_CUTOFF_REACHED` | Ano | Nejmenší delta, existující vzor, beze změny schématu a konfigurace | Změna hodnoty = redeploy; rollback = revert jednoho commitu | Hraniční testy s fixním `Clock` + TC-01/02 |
| **B: totéž, lhůta jako property `autorepo.cancellation-cutoff`** | Hodnota injektovaná do `ReservationRules` | Ano | Laditelné bez změny kódu | Statická `canCancel` volaná z entity → refaktor; riziko chybné konfigurace | Jako A + test nedefaultní a neplatné hodnoty |
| **C: beze změny kódu, jen notifikace fleet managera** | BR-03 v0.3 zůstává | **AC-02/03 ne** | Nulové riziko v kódu | Nesplňuje potřebu; notifikace je jen log stub | Ruční proces + přijetí mezery |
| *(vyřazeno)* D: stav `LATE_CANCELLED` + poplatek | | Částečně | Flexibilita | Nový stav a ceník, mimo rozsah | n/a |

**Důsledek nečinnosti:** platí BR-03 v0.3 a potvrzená auta lze rušit do poslední minuty. Jediná proveditelná odpověď bez kódu je C, a ta AC-02 nesplňuje.

### Peer review

- **Recenzent:** Adam Murcko
- **Review:** `TODO`
- ### Peer review — Adam Murcko, 2026-11-16

**Kontrolováno:** Change Card `CR-08` a `DEF-C08-01` v souboru `docs/intent-and-change.md` na commitu `<14d4fdd>`.

**Co jsem ověřil:**
- Prošel jsem `ReservationRules.canCancel`, `Reservation.cancel` a `ReservationService.cancelReservation`, abych ověřil, že zdokumentované dopady odpovídají aktuální implementaci.
- Spustil jsem `./mvnw -B test -Dtest=ReservationC08BaselineIntegrationTest` nad H2: 3 testy prošly a 1 byl přeskočen. Po povolení `TC-C08-04` test selhal s chybou `"expected EXPIRED but was PENDING_APPROVAL"`, což odpovídá zdokumentovanému pozorování.
- Zkontroloval jsem `list.html` a potvrdil, že tlačítko Cancel aktuálně používá `#temporals.createNow()`.

**Předpoklad 1 (limit = 2 hodiny, shodně s minimální dobou předem):** Souhlasím s jedním upřesněním. Rezervaci ve stavu `CONFIRMED` nelze zrušit v okamžiku přesně dvě hodiny před začátkem ani později. Tento limit akceptuji, protože rezervaci lze stále zrušit ve stavu `DRAFT`. Potvrzovací zpráva však musí uživatele na toto omezení upozornit. Považuji to za povinný úkol pro C09, nikoli za volitelné vylepšení.

**Předpoklad 2 (`PENDING_APPROVAL` je vyloučen):** Souhlasím. Vozidlo ještě není před schválením připraveno a rezervace čekající na schválení již mají vlastní lhůtu vypršení podle BR-06.

**Předpoklad 3 (samostatný kód chyby):** Částečně nesouhlasím. Nový kód chyby je vhodný, ale nová třída výjimky není nutná. Již existující `CancellationWindowClosedException` lze znovu použít s novým kódem `CANCELLATION_CUTOFF_REACHED`. Díky tomu není nutné měnit `ReservationViewController.handleAction` ani `GlobalExceptionHandler` a výsledná změna bude menší.

**Dodatečné zjištění — střední závažnost:** Matice dopadů nezmiňuje předpřipravenou rezervaci ve stavu `CONFIRMED` v `DataInitializer`. Začíná za tři dny, takže novým limitem není ovlivněna. Při demonstraci limitu však nebude k dispozici předpřipravená rezervace v rámci dvouhodinového okna. C09 by proto měl přidat demonstrační krok nebo vhodná testovací data. Toto zjištění nebrání schválení rozhodnutí.

**Dodatečné zjištění — nízká závažnost:** `AC-02` uvádí, že interval zůstává `UNAVAILABLE`, ale žádný test aktuálně neověřuje `OP-02` po zamítnutí zrušení. Nový test v C09 by měl toto ověření obsahovat.

**Verdikt:** SCHVALUJI variantu A s následujícími změnami:

- **C09-R1:** Upozornění při potvrzení rezervace musí být povinné podle předpokladu 1.
- **C09-R2:** Znovu použít `CancellationWindowClosedException` podle předpokladu 3 a odpovídajícím způsobem aktualizovat řádek varianty A i omezený rozsah změn.
- **C09-R3:** Ověřit stav `OP-02` po zamítnutém zrušení.
- Zjištění týkající se demonstračních dat přesunout do backlogu pro C09.

**DEF-C08-01:** Souhlasím, že jde o samostatnou opravu. Preferuji `@Transactional(noRollbackFor = ApprovalWindowExpiredException.class)` jako nejmenší změnu zajišťující zachování stavu v databázi. Notifikace o vypršení však musí být odeslána až po úspěšném commitu transakce.

Podepsán: Adam Murcko, 2026-11-15

### Rozhodnutí

- **Rozhodnutí:** **ACCEPT — varianta A**. **Vlastník:** Radim Pukalík. **Datum:** 2026-11-15.
- **Zdůvodnění:** A splňuje všechna AC s nejmenší ohraničenou deltou v rámci ADR-01, nevyžaduje migraci a je plně vratná. Jediný přínos B (ladění za běhu) není požadavek a znamená refaktor. C nesplňuje AC-02.

### Předání do C09 (při ACCEPT)

| Položka | Obsah |
|---|---|
| Before | `c8db481`, BR-03 v0.3, spuštěné TC-C08-01/02/03 (PASS, H2 + PostgreSQL 16.15) |
| Přijatá změna | storno `CONFIRMED` jen při `now < start − 2h`, jinak `CANCELLATION_CUTOFF_REACHED`, stav beze změny |
| Ohraničená delta | `ReservationRules`, `Reservation.cancel`, `ReservationService.cancelReservation`, `DomainErrorMessages`, `ReservationViewController.handleAction`, `list.html`/`detail.html`; dokumenty BR-03, OP-04, statechart, C05 (EN + CZ) |
| Nové orákly (očekávané výsledky) | `S−2h−1min` → `CANCELLED` + `AVAILABLE`; `S−2h` přesně → `CANCELLATION_CUTOFF_REACHED`; `S−1h` → totéž; `S` → `CANCELLATION_WINDOW_CLOSED`; `DRAFT`/`PENDING_APPROVAL` v `S−1h` → `CANCELLED` |
| Regrese | TC-C08-01, TC-C08-02, upravený TC-C08-03, celá stávající sada |
| Technické riziko | UI počítá povolení z jiných hodin než služba (R-05); souběh storna se schválením/potvrzením neověřen (R-01) |
| Práce v C09 | Implementační PR, integrační běh na PostgreSQL, clean checkout, ruční UI kontrola, rozhodnutí o release |

### Otevřená nejistota

Adamovo peer review. Pokud napadne hodnotu lhůty 2 h, změní se konstanta (stejná delta, jiná hodnota). Při požadavku na laditelnou lhůtu se vrátit k variantě B.

---

## C08 / DEF-C08-01 — Pozdní schválení: přechod do `EXPIRED` se rollbackuje (defekt nalezený v kroku 2)

- **Porušené tvrzení:** OP-05 *Window Expired*: „odmítnuto `APPROVAL_WINDOW_EXPIRED` (spouští přechod do `EXPIRED`)" a BR-06.
- **Pozorováno (TC-C08-04, H2 i PostgreSQL 16.15, `c8db481`):** výjimka se vyhodí podle specifikace, ale **commitnutý** stav zůstává `PENDING_APPROVAL`. Notifikace „expired" přesto **odešla**.
- **Příčina:** `approveReservation` je `@Transactional`. Zavolá `expire()`, `save()` a `flush()` a pak vyhodí `ApprovalWindowExpiredException` (`RuntimeException`), takže Spring provede rollback. Unit test s mocky (`approvalExpiresAtStartTime`) kontroluje jen objekt v paměti.
- **Dopad:** soft-lock drží až do dalšího běhu scheduleru (≤ 60 s). Uživatel dostane rozpornou notifikaci. BR-02 porušeno **není**.
- **Rozhodnutí:** **ACCEPT jako oprava defektu v C09**, odděleně od CR-08. **Vlastník:** Radim Pukalík. **Recenzent:** Adam Murcko (review čeká). **Datum:** 2026-11-15.
- **Možnosti opravy:** `noRollbackFor = ApprovalWindowExpiredException.class`, *nebo* zápis expirace v `REQUIRES_NEW`, *nebo* vracet výsledek místo výjimky. Notifikaci vždy posílat až po commitu.
- **Orákl (očekávaný výsledek):** odstranit `@Disabled` z `tcC0804_lateApprovalPersistsExpired`; test musí projít na PostgreSQL.

## C08 — Známá omezení before baseline (CR-08 je nemění)

- **Chybí model rolí / autorizace:** OP-05/06 vyžadují roli Approver a uložení `approver_id`, ale `User` nemá `role`, nic nekontroluje aktéra a `approver` se nikdy nenastaví. Kroky s podmínkou role jsou na tomto baseline **BLOCKED**.
- **Drift hodin (R-05):** služba používá UTC `Clock`, ale `DataInitializer`, `ReservationLifecycleIntegrationTest` a `list.html` (`#temporals.createNow()`) lokální čas JVM.
- **Text BR-03 vs. kód:** BR-03 bod 4 uvádí `INVALID_STATE_TRANSITION`, kód vrací `ALREADY_CANCELLED`.
- **Souběh (R-01):** test souběžného potvrzení zatím nebyl spuštěn.
