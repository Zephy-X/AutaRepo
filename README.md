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
2. **Run the application**
   Use Maven wrapper to start the app:
   ```bash
   ./mvnw spring-boot:run
3. ## How to Stop (Graceful Shutdown)
Aby si aplikáciu a databázu bezpečne zastavil bez straty dát:

1. **Zastavenie aplikácie (Spring Boot)**  
   V termináli (PowerShell), kde beží príkaz Maven, stlač `Ctrl + C`. Spring Boot bezpečne ukončí všetky procesy a odpojí sa od databázy.

2. **Zastavenie databázy (Docker)**  
   Otvor nový terminál v priečinku s projektom a použi príkaz:
   ```bash
   docker compose down

## POST /reservations
→ validate input data
→ persist into PostgreSQL database
→ return generated reservation ID (e.g. 201 Created)
→ automated/REST check via GET /reservations

## Definition of Done (Stav pred C02)

[x] Tým 3–4 členové
[x] Spoločný repozitár (Zephy-X/AutaRepo)
[x] Jasný reservation domain (Rezervácia vozidiel)
[x] Resource (Vehicle) + Reservation + User (userId)
[x] Zmysluplné stavy rezervácie (DRAFT, CONFIRMED, CANCELLED)
[x] Operácie (create, confirm/approve, cancel, availability cez REST API)
[x] Common overlap rule (definované v Project Frame)
[x] 1 domain-specific business rule (definované v Project Frame)
[x] 1 external/system boundary (Notification Service definovaná v Project Frame)
[x] Kompletný Project Frame v docs/intent-and-change.md
[x] 1 Q/C/R/L future pressure (zvolená Q - Quality/Scale)
[x] 1 reviewed and integrated change (formálny code review v tíme)
[x] 1 vykonaný engineering spike (Spike A – pripojenie PostgreSQL a uloženie entity)
[x] Spike evidence + decision v docs/evidence-and-evolution.md
[x] Definovaný CP1 walking skeleton v README.md