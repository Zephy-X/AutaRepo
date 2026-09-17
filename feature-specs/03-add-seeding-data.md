I need to automatically populate my PostgreSQL database with seed data whenever the application starts up if the database is currently empty.

### Requirements:

1. Create a Spring bean implementing `CommandLineRunner` or `ApplicationRunner` (e.g., `DataInitializer.java`) in my project.
2. In the runner method, check if data already exists (e.g., `userRepository.count() == 0`):
    - **Users:** Create 2-3 sample drivers with realistic names, emails, and license numbers (e.g., `John Doe`, `Jane Smith`).
    - **Vehicles:** Create 3-4 sample vehicles with realistic brands, models, years, VINs (17 chars), and license plates (e.g., `Skoda Octavia`, `Toyota Corolla`, `Volkswagen Golf`).
    - **Reservations:** Create at least 1-2 realistic reservations linking the created users and vehicles (e.g., one upcoming `CONFIRMED` reservation and one `DRAFT`).
3. Ensure entities are saved in the correct dependency order (`User` and `Vehicle` before `Reservation`) within a `@Transactional` block.
4. Mark the bean so it runs safely in local/dev profiles without duplicating records on every restart.