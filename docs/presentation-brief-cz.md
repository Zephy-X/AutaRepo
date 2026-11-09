# Prezentační brief — AutoRepo C08

## Cíl prezentace

Prezentace má ukázat, že základní životní cyklus rezervace je implementovaný,
opravy z C06 jsou uzavřené a testy procházejí i s PostgreSQL v Dockeru. Status
zůstává `AMBER`, protože stále chybí samostatný důkaz konkurence, schedulerové
runtime expirace a ověření na druhém čistém prostředí.

Doporučený čas: **8–10 minut**. Text ze slidů nečti doslova; u každého slidu
vysvětli jednu hlavní myšlenku a jeden konkrétní důkaz.

## Úvod — přibližně 30 sekund

> „Představuji stav projektu AutoRepo v rámci C08. Zaměřím se na rezervační
> životní cyklus, opravy z předchozího hodnocení a na jejich ověření testy.
> Aktuální status je AMBER: jádro funguje a celý test suite prošel, ale stále
> máme důkazní mezery u konkurence, schedulerové expirace a reprodukovatelnosti.“

## Slide 1 — Executive Project Status

**Hlavní pointa:** projekt je funkčně zdravý, ale ještě není možné tvrdit
`GREEN`.

Řekni:

> „F1 až F3 z C06 jsou zpracované. Po spuštění Dockeru je PostgreSQL healthy a
> `mvnw.cmd -q test` skončil úspěšně. AMBER zůstává proto, že celý suite
> ověřuje běžné integrační scénáře, ne speciálně současné potvrzení dvou
> rezervací ani úplný runtime důkaz schedulerové expirace.“

Zdůrazni rozdíl:

- **Implementováno:** kód a běžné integrační scénáře existují.
- **Ověřeno:** konkrétní test nebo reprodukovatelný důkaz potvrdil chování.

## Slide 2 — Scope & Delivery Evidence

**Hlavní pointa:** hlavní funkce mají specifikaci, návrh, implementaci i
základní testy; invariant BR-02 má stále otevřený důkaz konkurence.

Řekni:

> „Create, availability, confirm/approval a cancel jsou pokryté kódem a testy.
> U BR-02 je implementovaný overlap check a PostgreSQL integrace funguje,
> ale nemáme dedikovaný test, který by spustil dva confirm requesty současně
> na stejné vozidlo. Proto je tato položka PARTLY, ne YES.“

Když se vyučující zeptá na PostgreSQL:

> „Předchozí selhání nebylo způsobené Hibernate ani business logikou. Kontejner
> neběžel. Po spuštění Dockeru se persistence test připojil na PostgreSQL 16
> a celý Maven suite prošel.“

## Slide 3 — Schedule, Resources & Dependencies

**Hlavní pointa:** opravy C06 jsou hotové; další krok je důkaz, ne velká nová
funkce.

Řekni:

> „Při správně spuštěném prostředí už není hlavní závislostí dostupnost
> databáze. Kritické zůstávají tři kontrolní body: paralelní potvrzení,
> schedulerová expirace a ověření na druhém čistém prostředí.“

Netvrď, že konkurence nebo scheduler jsou hotové pouze proto, že odpovídající
metody jsou implementované. Mluv o nich jako o otevřených ověřovacích úlohách.

## Slide 4 — Top Risks

**Hlavní pointa:** rizika jsou neprokázané scénáře, ne potvrzené chyby.

- `R-01`: současné confirm transakce na stejném vozidle.
- `R-02`: scheduler může být spuštěn pozdě nebo neověřen na hranici deadline.
- `R-03`: build může záviset na lokálním prostředí autora.

Řekni:

> „Nezaměňuji plánované opatření za snížení rizika. Dokud nemáme provedený
> důkaz, skutečný residual status je NOT VERIFIED.“

## Slide 5 — Next Action Plan

**Hlavní pointa:** každé otevřené riziko má vlastníka, termín a měřitelný
výsledek.

Řekni:

> „P1 má dokázat, že paralelní potvrzení neporuší BR-02. P2 má dokázat
> deadline, přechod do EXPIRED a uvolnění soft-locku. P3 má odstranit
> závislost na jednom lokálním prostředí. Teprve potom lze AMBER reálně
> přehodnotit.“

## Slide 6 — Management Conclusion

**Hlavní pointa:** rozhodnutí je podmíněné pokračování, ne úplné uzavření
rizik.

Závěr:

> „Základní rezervační životní cyklus je implementovaný, opravy F1 až F3 jsou
> uzavřené a celý test suite prošel s PostgreSQL v Dockeru. Navrhuji
> pokračovat směrem k CP2 podmíněně: před finálním sign-off je třeba doplnit
> concurrency test, schedulerový runtime důkaz a reprodukci na druhém
> prostředí. Proto je výsledný status AMBER.“

## Technická osa pro otázky

Pokud bude čas na detail, veď odpověď přes `OP-03 Confirm Reservation`:

```text
ReservationViewController
  -> ReservationService.confirmReservation(id)
  -> načtení rezervace a kontrola DRAFT
  -> VehicleService.findByIdForUpdate(...)
  -> kontrola active + future start
  -> ReservationRepository.hasStateConflict(...)
  -> confirm(...) nebo requestApproval(...)
  -> saveAndFlush(...)
  -> notifikace
```

Klíčové pravidlo je `BR-02`: pouze `CONFIRMED` a `PENDING_APPROVAL` blokují
vozidlo a intervaly používají half-open semantiku `[start, end)`. Rezervace,
která začíná přesně v čase `end` předchozí rezervace, tedy není konflikt.

## Nejpravděpodobnější otázky a krátké odpovědi

**Proč AMBER, když všechny testy prošly?**  
Úspěšný suite dokazuje provedené scénáře, ale nemáme dedikovaný stress test
pro souběžné PostgreSQL transakce, runtime důkaz schedulerové expirace ani
druhé čisté prostředí.

**Co přesně vyřešil Docker?**  
Zpřístupnil PostgreSQL kontejner. Bez něj Hibernate nemohl získat JDBC metadata
a určit dialect. Po spuštění kontejneru se databázové testy připojily a suite
prošel.

**Proč je `PENDING_APPROVAL` blokující stav?**  
Je to soft-lock. Zabraňuje, aby jiná rezervace obsadila stejný interval během
čekání na schválení.

**Co bylo F1?**  
Potvrzení předtím nekontrolovalo aktivitu vozidla a začátek rezervace. Nyní se
odmítne neaktivní vozidlo i rezervace, jejíž začátek již nastal.

**Co bylo F2?**  
Šlo o nesoulad návrhu a implementace: pravidla nebyla delegována přes
`ReservationRules` a konflikt nepoužíval `ConflictOverlapException`. Obě části
byly sjednoceny.

**Co bylo F3?**  
Chyběly konzistentní evidence a baseline/reporting údaje. Doplnění dokumentace
tuto mezeru uzavřelo.

**Je `FOR UPDATE` důkaz, že je konkurence vyřešená?**  
Ne samostatně. Je to důležitá část návrhu pro serializaci přístupu, ale
definitivní důkaz vyžaduje paralelní PostgreSQL test s pozorovaným výsledkem.

## Co mít otevřené před prezentací

- `docs/SWI_C08_Project_Status_FILLED2.pptx`
- `docs/technical-defense-brief-cz.md`
- `src/main/java/.../ReservationService.java`
- `src/main/java/.../Reservation.java`
- `src/main/java/.../common/domain/ReservationRules.java`
- `src/test/java/.../ReservationServiceTest.java`
- `src/test/java/.../ReservationPersistenceSpikeTest.java`

Před ukázkou prostředí spusť:

```powershell
docker compose ps
.\mvnw.cmd -q test
```

Na otázky odpovídej podle důkazů. Pokud něco nebylo samostatně otestované,
řekni „implemented, but not yet independently verified“ místo tvrzení, že je
to úplně prokázané.
