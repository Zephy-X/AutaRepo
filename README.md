# Vehicle Reservation System

**Team Name:** AutaRepo

**Members:**

- Adam Murcko
- Radim Pukalík
- Zuzka Plaskova

## Technology Stack

- **Runtime:** Java 21 (LTS)
- **Framework:** Spring Boot 3.x
   - `spring-boot-starter-web` (Spring MVC controllers & view resolution)
   - `spring-boot-starter-thymeleaf` (Server-side rendered HTML UI)
   - `spring-boot-starter-data-jpa` (Hibernate ORM & automated repositories)
   - `spring-boot-starter-validation` (Jakarta Bean Validation)
   - `postgresql` runtime driver
- **Frontend UI & Styling:**
   - **Template Engine:** Thymeleaf
   - **CSS Framework:** Tailwind CSS (via CDN)
   - **Component Library:** DaisyUI (via CDN, semantic utility classes `btn`, `table`, `card`, `alert`, `badge`)
   - **Theme Preset:** DaisyUI built-in theme (e.g., `dim` or `nord` declared via `data-theme`)
- **Database:** PostgreSQL 16 (executed via Docker Compose)
- **Database Management:** pgAdmin 4 (executed via Docker Compose)
- **Testing:** JUnit 5 + Spring Boot Starter Test
- **Build Tool:** Maven
- **Architectural Style:** Monolithic Server-Side Rendered (SSR) Web App (No detached REST API)
- **Design Pattern:** Package-by-Feature with strict Layered Separation (`Controller` → `Service` → `Repository` → `Database`)

---

## CP1 Walking Skeleton

- **POST `/reservations`**
   - Validate input & constraints (advance window $\ge$ 2 hours, max duration $\le$ 14 days, no overlapping bookings for the vehicle).
   - Persist reservation to PostgreSQL (`state = CONFIRMED`).
   - Redirect to `/reservations` with a flash success message showing the reservation ID (HTTP 302/303).
   - Automated check: `ReservationPersistenceSpikeTest` executes via `@SpringBootTest` asserting saved entities, relational mapping, and timestamp integrity.

---


# Quick Start

### Prerequisites 

- **Java 21**
- **Docker** 

> **Tip:** Open the terminal in IntelliJ with `Alt + F12`.

---

## 1. Start Database & Tools

```bash
docker compose up -d
```

* **App DB:** `localhost:5432` (`autorepodb`)
* **pgAdmin:** [http://localhost:5050](http://localhost:5050)
  * **pgAdmin Login:**
    * **Email:** `admin@autorepo.com`
    * **Password:** `admin`
  * **PostgreSQL Connection (inside pgAdmin):**
    * **Username:** `postgres`
    * **Password:** `mysecretpassword`

## 2. Run the Reservation Spike Test

```bash
./mvnw test -Dtest=ReservationPersistenceSpikeTest
```

---

## 3. Run the Application

```bash
./mvnw spring-boot:run
```

> **Note:** Run via `./mvnw spring-boot:run` at least on the first startup so the database initializes with default seed data.

Access the application at: [http://localhost:8080/vehicles](http://localhost:8080/vehicles)

---

## 4. Stop Environment & Reset Data

```bash
docker compose down -v
```

---

# Definition of Done (Stav pred C02)

- [x] Tým 3–4 členové
- [x] Spoločný repozitár (`Zephy-X/AutaRepo`)
- [x] Jasný reservation domain (Rezervácia vozidiel)
- [x] Resource (`Vehicle`) + Reservation + User (`userId`)
- [x] Zmysluplné stavy rezervácie (`DRAFT`, `CONFIRMED`, `CANCELLED`)
- [x] Operácie (`create`, `confirm`/`approve`, `cancel`, availability cez REST API)
- [x] Common overlap rule (definované v Project Frame)
- [x] 1 domain-specific business rule (definované v Project Frame)
- [x] 1 external/system boundary (Notification Service definovaná v Project Frame)
- [x] Kompletný Project Frame v `docs/intent-and-change.md`
- [x] 1 Q/C/R/L future pressure (zvolená Q - Quality/Scale)
- [ ] 1 reviewed and integrated change (formálny code review v tíme)
- [x] 1 vykonaný engineering spike (Spike A – pripojenie PostgreSQL a uloženie entity)
- [x] Spike evidence + decision v `docs/evidence-and-evolution.md`
- [x] Definovaný CP1 walking skeleton v `README.md`