# C01 Engineering Spike

## Question / unknown

Can our Spring Boot 3 + Java 21 application connect to PostgreSQL 16 running in Docker, generate the required schema via Hibernate, and successfully persist and reload a complete `Reservation` entity graph (`Reservation` -> `Vehicle` + `User`)?

## What we did

1. Configured `docker-compose.yml` with PostgreSQL 16 and pgAdmin 4.
2. Created entity models (`Vehicle`, `User`, `Reservation`) using Jakarta Persistence and Spring Data JPA repositories.
3. Implemented an automated integration test `ReservationPersistenceSpikeTest.java` (Spike A) that builds a test reservation, saves it to PostgreSQL, and re-reads it using `findById`.

## Observed result

The test executed successfully. The PostgreSQL container accepted connections on `localhost:5432`, Hibernate automatically applied DDL updates, and the saved reservation was retrieved with matching IDs, timestamps, and foreign key relations.

## Decision / what changes because of the result

We confirm that Dockerized PostgreSQL with Spring Data JPA satisfies our persistence requirements. We will proceed with this baseline stack for the CP1 walking skeleton without introducing extra database migration tooling until needed.
