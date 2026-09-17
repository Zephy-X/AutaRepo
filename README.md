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

## CP1 Walking Skeleton

- **POST `/reservations`**
   - Validate input & constraints (advance window $\ge$ 2 hours, max duration $\le$ 14 days, no overlapping bookings for the vehicle).
   - Persist reservation to PostgreSQL (`state = CONFIRMED`).
   - Redirect to `/reservations` with a flash success message showing the reservation ID (HTTP 302/303).
   - Automated check: `ReservationPersistenceSpikeTest` executes via `@SpringBootTest` asserting saved entities, relational mapping, and timestamp integrity.

---

## How to Build and Run (Reproducible Build)

### Prerequisites

- **Java 21** (JDK installed and available on `$PATH`)
- **Docker** & **Docker Compose** installed and running

---

### Step-by-Step Instructions

1. **Start the database & management tools**  
   Start PostgreSQL and pgAdmin containers in the background:

   ```bash
   docker compose up -d
   ```

   - **PostgreSQL:** `localhost:5432` (database: `autorepodb`, user: `postgres`, password: `mysecretpassword`)
   - **pgAdmin Console:** `http://localhost:5050`

2. **Run tests & verification suite**  
   Execute unit and integration tests (including the persistence spike test):

   ```bash
   # Linux / macOS
   ./mvnw clean test

   # Windows
   mvnw.cmd clean test
   ```

3. **Build the application**  
   Compile and package the runnable JAR file:

   ```bash
   # Linux / macOS
   ./mvnw clean package

   # Windows
   mvnw.cmd clean package
   ```

4. **Run the application**  
   Start the Spring Boot web application:

   ```bash
   # Option A: Run directly via Maven
   ./mvnw spring-boot:run

   # Option B: Run the packaged JAR
   java -jar target/autorepo-0.0.1-SNAPSHOT.jar
   ```

   - The web application is accessible at: `http://localhost:8080/vehicles`

5. **Tear down the environment**  
   Stop and remove containers when finished:
   ```bash
   docker compose down
   ```
   _(Optional: append `-v` to reset all database volume data: `docker compose down -v`)_
