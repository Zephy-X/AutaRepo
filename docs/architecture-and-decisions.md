# Architecture and Decisions

## System Architecture
Systém je navrhnutý ako **REST API** postavené na architektúre klient-server. Využívame štandardný vrstvený model (Controller -> Service/Repository -> Database).

## Technology Stack & Baseline Decisions

### 1. Backend Framework
- **Rozhodnutie:** Java 21 + Spring Boot (Web, Data JPA).
- **Dôvod:** Spring Boot umožňuje extrémne rýchly štart projektu (ideálne pre Walking Skeleton). Poskytuje robustné nástroje pre tvorbu REST API a jednoduchú integráciu s databázou cez Hibernate/JPA.

### 2. Databáza a Perzistencia
- **Rozhodnutie:** PostgreSQL 16 (spúšťaný lokálne cez Docker).
- **Dôvod:** Rezervačný systém je doména so silne relačnými dátami (používateľ -> rezervácia -> vozidlo). PostgreSQL zaručuje dátovú integritu, podporuje pokročilé zamykanie (potrebné pre kontrolu prekrývania termínov) a Docker zabezpečuje konzistentné prostredie pre všetkých členov tímu (Reproducible Build).

### 3. API Design
- **Rozhodnutie:** RESTful prístup s formátom JSON.
- **Dôvod:** Štandard pre webové aplikácie, jednoducho testovateľný cez curl/PowerShell a pripravený na prípadné napojenie frontendového klienta v budúcich fázach.

## Future Architectural Considerations
V ďalších fázach (po C02) bude potrebné architektonicky doriešiť napojenie na externú `Notification Service` a implementáciu zložitejších biznis pravidiel (Transaction management).