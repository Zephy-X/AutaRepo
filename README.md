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