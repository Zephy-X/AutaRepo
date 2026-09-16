# Project Frame

## Reservation domain
Rezervácia firemných a zdieľaných motorových vozidiel (autá pre zamestnancov/študentov).

## Purpose
Systém slúži zamestnancom a oprávneným používateľom na jednoduché plánovanie, rezerváciu a správu dostupnosti firemných vozidiel. Zabezpečuje prehľad o obsadenosti áut a zabraňuje kolíziám v termínoch.

## Users / Stakeholders
1. **Zamestnanec / Používateľ** — vytvára a spravuje svoje rezervácie vozidiel.
2. **Správca flotily (Admin)** — schvaľuje a dohliada na rezervácie a stav vozidiel.

## Core concepts
- **Reservation** (Identita, časový slot, stav)
- **Vehicle / Resource** (Konkrétne vozidlo, ŠPZ, značka, model)
- **User** (Identifikátor používateľa, ktorý rezerváciu vytvoril)

## Core operations
- Create reservation (`POST /reservations`)
- Confirm / approve reservation (`PUT /reservations/{id}/confirm`)
- Cancel reservation (`DELETE /reservations/{id}`)
- Check availability (`GET /reservations`)

## Persistent state
O rezervácii ukladáme: ID, ID používateľa, prepojené vozidlo, čas začiatku a konca, aktuálny stav (DRAFT, CONFIRMED, CANCELLED). O vozidle ukladáme: ID, značku, model a EČV (ŠPZ).

## State-changing operation
`DRAFT` → `CONFIRMED` (potvrdenie rezervácie správcom alebo systémom).

## Common business rule
Dve potvrdené rezervácie pre to isté vozidlo sa nesmú časovo prekrývať.

## Domain-specific business rule
Vozidlo nemôže byť rezervované na dobu dlhšiu ako 7 po sebe nasledujúcich dní bez schválenia administrátorom.

## External / system boundary
**Notification Service** — externá služba/modul na odosielanie e-mailových alebo systémových potvrdení o vytvorení/zrušení rezervácie.

## Assumption
Predpokladáme, že všetci používatelia sú už overení prostredníctvom existujúceho podnikového identitného systému (SSO), takže správa samotných používateľov nie je primárnou súčasťou tohto systému.

## Unknown
Zatiaľ presne nevieme, aké budú presné integračné požiadavky na externý kalendár (napr. Google Calendar / Outlook), ktorý budeme musieť v neskorších fázach integrovať.

## Selected future pressure
- **Category:** Q (Quality / Scale)
- **Concrete pressure:** 10× nárast súbežných požiadaviek na rezervácie v čase špičky (napr. piatkové odchody).
- **Why it is relevant to our reservation system:** Donúti nás to optimalizovať databázové indexy pre časové sloty a vyriešiť efektívne zamykanie zdrojov pri kontrole prekrývania termínov.