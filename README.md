# Vehicle Reservation System (AutaRepo)

## Team
**Team Name:** AutaRepo

**Members:**
* Adam Murcko
* Radim Pukalík
* Zuzka Plaskova

## Technology Stack
* **Java 21**
* **Spring Boot** (Web, Data JPA)
* **PostgreSQL** (Docker)
* **Maven**

## How to Build and Run (Reproducible Build)
Prerequisites: Make sure you have Docker and Java 21 installed on your system.

Follow these steps for a clean build and run:

1. **Start the database**  
   Run the PostgreSQL container in the background:
   ```bash
   docker compose up -d

## Definition of Done (Stav pred C02)

- [x] Tým 3–4 členové
- [x] Spoločný repozitár (`Zephy-X/AutaRepo`)
- [x] Jasný reservation domain (Rezervácia vozidiel)
- [x] Resource (`Vehicle`) + Reservation + User (`userId`)
- [x] Zmysluplné stavy rezervácie (`DRAFT`, `CONFIRMED`, `CANCELLED`)
- [ ] Operácie (`create` hotové, `confirm/approve`, `cancel` a `availability` čakajú na implementáciu)
- [ ] Common overlap rule (kontrola prekrývania termínov)
- [ ] 1 domain-specific business rule (vlastné biznis pravidlo)
- [ ] 1 external/system boundary (Notification Service)
- [ ] Kompletný Project Frame v `docs/intent-and-change.md`
- [ ] 1 Q/C/R/L future pressure (budúci tlak/záťaž)
- [ ] 1 reviewed and integrated change (formálny code review v tíme)
- [x] 1 vykonaný engineering spike (Spike A – pripojenie PostgreSQL a uloženie entity)
- [ ] Spike evidence + decision v `docs/evidence-and-evolution.md`
- [ ] Definovaný CP1 walking skeleton v `README.md`