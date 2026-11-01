# Detailní doménová pravidla a invariants

| Rule ID | Název | Základní princip | Klíčové vynucení a omezení |
| :--- | :--- | :--- | :--- |
| **BR-01** | Interval Semantics | Polouzavřené intervaly: $[start, end)$. | čisté hrany v přesném čase; $start < end$ je povinné (žádné nulové nebo obrácené sloty). |
| **BR-02** | Resource Invariant & Locking | Pro stejný vůz nesmí existovat žádné překrývající se blokující stavy. | Pevné zamčení: `CONFIRMED`.<br>Jemné zamčení: `PENDING_APPROVAL`.<br>Oba stavy blokují dostupnost a zabraňují dvojímu rezervování. |
| **BR-03** | Cancellation Policy | Dobrovolné stažení před startem cesty. | Povolené jen tehdy, když $	ext{now} < 	ext{start}$ v stavech `DRAFT`, `PENDING_APPROVAL` nebo `CONFIRMED`. Poté se okamžitě uvolní veškeré inventarové zámky. |
| **BR-04** | Draft Intent | Předměty v košíku drží nulový inventář. | Více uživatelů může mít stejný vůz ve stavu draft současně. Slot je přidělen na základě „first-come, first-served“ při potvrzení (`OP-03`). |
| **BR-05** | Luxury Vehicle Policy | Vozidla s vyšší hodnotou vyžadují dohled manažera. | Standardní vozy se potvrzují okamžitě; luxusní vozy přecházejí do `PENDING_APPROVAL` s výhradním soft-lockem do schválení nebo odmítnutí. |
| **BR-06** | Automatic Expiration (TTL) | Čekající požadavky nesmí viset donekonečna. | Automaticky se zruší do `EXPIRED`, pokud uplyne review SLA nebo pokud nastane čas startu bez rozhodnutí. Soft-lock se okamžitě uvolní. |
| **BR-07** | Physical Exclusivity | Kapacita každého vozidla = 1. | Přísné 1:1 mapování fyzického zdroje; žádné sdílené ani zlomkové alokace. |

## BR-01 — Interval Semantics
Všechny rezervace a intervaly dostupnosti v systému se řídí přísně polouzavřenou sémantikou: $[start, end)$.

- **Pokrytí:** Rezervace naplánovaná na $[10:00, 12:00)$ zabírá zdroj od 10:00:00.000 až do 11:59:59.999.
- **Přilehlost:** Sousední rezervace začínající v 12:00:00.000 (např. $[12:00, 14:00)$) se nepřekrývá s intervalem $[10:00, 12:00)$. Přechod přes přesnou hranici je validní.
- **Syntaktická validita:** Interval je platný pouze tehdy, pokud $start < end$.
    - Nulové intervaly ($start = end$) jsou přísně neplatné.
    - Obrácené intervaly ($start > end$) jsou přísně neplatné.

---

## BR-02 — Exclusive Resource Invariant & Blocking States
V žádném committed stavu systému nesmí pro stejný exklusivní zdroj (auto) existovat dvě překrývající se blokující rezervace.

- **Definice překryvu:** Překryv mezi intervalem $A = [A.start, A.end)$ a $B = [B.start, B.end)$ nastává tehdy a jen tehdy, pokud:
  $$\max(A.start, B.start) < \min(A.end, B.end)$$

- **Blokující stavy (Inventory Claims):**
    - `CONFIRMED` (Hard Lock): trvalé vázané alokování přidělené uživateli.
    - `PENDING_APPROVAL` (Soft Lock): výhradní předběžné držení udělené během čekání na rozhodnutí schvalovatele. Blokuje dostupnost (`OP-02`) a zabraňuje jinému uživateli vytvořit nebo potvrdit překrývající se slot.

- **Neblokující stavy:**
    - Rezervace ve stavech `DRAFT`, `CANCELLED`, `REJECTED` nebo `EXPIRED` drží nula inventáře a neblokují dostupnost.

- **Hranice vynucení:**
    - Vynucováno během `OP-03 Confirm Reservation`. Rezervace nesmí přejít do `CONFIRMED` nebo `PENDING_APPROVAL`, pokud existuje překrývající se záznam v `CONFIRMED` nebo `PENDING_APPROVAL`.
    - Protože `PENDING_APPROVAL` vyžaduje výhradní soft-lock při odeslání potvrzení, následné povýšení do `CONFIRMED` přes `OP-05` je zaručeno bez kolizí.

---

## BR-03 — Cancellation Policy
Rezervaci může uživatel nebo schvalovatel dobrovolně zrušit pouze tehdy, pokud jsou splněny všechny následující podmínky:

1. **Přípustné stavy:** $\text{Reservation.state} \in \{\text{DRAFT}, \text{PENDING\_APPROVAL}, \text{CONFIRMED}\}$.
2. **Časové okno:** Aktuální systémový čas musí být striktně před startem rezervace:
   $$\text{currentTime} < \text{Reservation.start}$$
3. **Dopad na inventář:** Po přechodu do `CANCELLED` se okamžitě uvolní všechny nároky (soft-lock i hard-lock). `OP-02 Check Availability` okamžitě uvidí interval jako volný.
4. **Konečný stav a idempotence:** Odeslání zrušení pro rezervaci, která je už v terminalním stavu (`CANCELLED`, `REJECTED` nebo `EXPIRED`), se odmítne s `INVALID_STATE_TRANSITION`.
5. **Hraniční cutoff:** Pokud $\text{currentTime} \ge \text{Reservation.start}$, dobrovolné zrušení je odmítnuto s `CANCELLATION_WINDOW_CLOSED`.

---

## BR-04 — Draft Intent & Non-Allocation
1. **Účel:** Vytvoření rezervace (`OP-01`) zaznamenává záměr uživatele rezervovat vozidlo (uložit ho do košíku) bez závazného přidělení zdroje.
2. **Žádný nárok na inventář:** `DRAFT` rezervace neuzamyká auto a nemá žádný blokující účinek při kontrolách dostupnosti (`OP-02`).
3. **Současná existence:** Více uživatelů může zároveň držet překrývající se `DRAFT` rezervace pro stejné vozidlo a stejný interval.
4. **Bod vyřešení:** Soutěž mezi překrývajícími se drafty je vyřešena na základě první potvrzené rezervace při `OP-03`. První podání, které projde kontrolou konfliktu, získá vozidlo (buď přímo do `CONFIRMED`, nebo jako soft-lock v `PENDING_APPROVAL`). Všechny konkurenční drafty, které se pokusí potvrdit později, se odmítnou s `RESOURCE_UNAVAILABLE`.

---

## BR-05 — Luxury Vehicle Policy & Authorization Lifecycle
Zdroje označené jako `resource.isLuxury == true` vyžadují administrativní dohled před tím, než bude konečné závazné přidělení dokončeno.

1. **Routing:** Při `OP-03` přechází běžné zdroje přímo do `CONFIRMED`. Luxusní zdroje přecházejí do `PENDING_APPROVAL`.
2. **Exkluzivita během revize:** `PENDING_APPROVAL` vytváří platný soft-lock podle BR-02, který chrání uživatele před tím, aby mu někdo vyfoukl slot během čekání na schválení.
3. **Akce schvalovatele:**
    - **Schválení (`OP-05`):** Oprávněný schvalovatel (Fleet Manager) povýší `PENDING_APPROVAL` do `CONFIRMED` a přemění soft-lock na trvalé hard allocation.
    - **Odmítnutí (`OP-06`):** Schvalovatel žádost zamítne. Stav přejde do `REJECTED`, soft-lock se okamžitě uvolní a stav se stane terminalním.

---

## BR-06 — Time-to-Live (TTL) & Automatic Expiration
Přetížení luxusních rezervací nesmí držet vozidlo donekonečna, pokud zůstane nevyřízené.

1. **Review deadline:** Každé rezervaci vstupující do `PENDING_APPROVAL` je přiřazen automatický review deadline:
   $$\text{reviewDeadline} = \min(\text{submittedAt} + \Delta T_{\text{SLA}}, \text{Reservation.start})$$
   *(kde $\Delta T_{\text{SLA}}$ je timeout pro review, např. 24 hodin)*.
2. **Automatické vypršení (`OP-07`):** Pokud neexistuje rozhodnutí schvalovatele a:
   $$\text{currentTime} \ge \text{reviewDeadline} \quad \lor \quad \text{currentTime} \ge \text{Reservation.start}$$
   systém automaticky převede rezervaci do `EXPIRED`.
3. **Dopad na inventář:** Přechod do `EXPIRED` okamžitě zruší soft-lock a vrátí interval zdroje do otevřené dostupnosti (`OP-02`).

---

## BR-07 — Resource Exclusivity Assumption
- Každý zdroj (auto) je považován za jedinečný, nefungibilní a výhradní fyzický entitu s kapacitou 1.
- Kapacitní, sdílené nebo zlomkové alokace jsou zcela mimo rozsah.
