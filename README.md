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

# Definition of Done — Before C03

> **Note:** Teacher recommended plantUML for diagrams, i feel like mermaid code directly in our codebase would be better (example in docs/architecture/erd.mmd)

- [x] All four core operations have complete textual specification slices
- [x] Every accepted requirement passed the requirement acceptance gate
- [x] Shared business rules/invariants are defined once
- [x] One use-case/actor-goal view covers the minimum system
- [x] One Reservation lifecycle statechart covers the minimum system
- [x] Slices, requirements, use-case view, and statechart are mutually consistent
- [x] Baseline v0.1 was explicitly accepted by the team
- [x] The AI-generated app demonstrates all four core operations
- [x] One success + one relevant negative/boundary example was actually executed for every slice
- [x] Change impact was analyzed before editing
- [x] Affected and unaffected parts are explicit
- [x] Any new Approve slice is fully specified
- [x] Use-case view and statechart are updated for v0.2
- [x] Running app matches accepted Baseline v0.2
- [x] Evidence links specification to running behavior
- [x] At least one concrete architecture driver is ready for C03
